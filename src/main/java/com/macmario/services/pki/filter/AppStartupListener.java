package com.macmario.services.pki.filter;

import com.macmario.services.pki.service.BackupService;
import com.macmario.services.pki.service.ConfigService;
import com.macmario.services.pki.service.UserService;
import com.macmario.services.pki.util.EntityManagerProvider;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebListener
public class AppStartupListener implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(AppStartupListener.class);

    private final BackupService backupService = new BackupService();
    private final ConfigService configService = new ConfigService();
    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("=== MH Service PKI Manager starting ===");
        try {
            EntityManagerProvider.init();
            seedDefaultConfig();
            seedDefaultAdminUser();
            startAutoBackup();
            log.info("=== PKI Manager ready ===");
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            throw new RuntimeException("PKI Manager startup failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("=== PKI Manager shutting down ===");
        stopAutoBackup();
        EntityManagerProvider.close();
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        log.info("Removed BouncyCastle security provider");
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == cl) {
                try {
                    DriverManager.deregisterDriver(driver);
                    log.info("Deregistered JDBC driver: {}", driver);
                } catch (SQLException e) {
                    log.error("Error deregistering JDBC driver", e);
                }
            }
        }
    }

    private void seedDefaultConfig() {
        try (Connection c = EntityManagerProvider.getConnection()) {
            seedIfAbsent(c, "global.role", "master,subRoot1", "Active CA roles (pki.conf [global] role=)");
            seedIfAbsent(c, "crl.validity.days", "30", "CRL validity in days");
            seedIfAbsent(c, "cert.expiry.warn.days", "30", "Days before expiry to warn");
            seedIfAbsent(c, "org.name", "MHService", "Organisation name");
            seedIfAbsent(c, "backup.auto.enabled", "true", "Write a daily automatic backup when data changed");
            seedIfAbsent(c, "backup.auto.time", "22:05", "Time of day (HH:mm) for the daily automatic backup");
            seedIfAbsent(c, "backup.auto.keep", "20", "Number of automatic backups to retain");
        } catch (SQLException e) {
            log.warn("Could not seed config: {}", e.getMessage());
        }
    }

    // ── change-triggered automatic backups ────────────────────────────────────

    private static final LocalTime DEFAULT_BACKUP_TIME = LocalTime.of(22, 5);

    private void startAutoBackup() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pki-auto-backup");
            t.setDaemon(true);
            return t;
        });
        scheduleDaily(); // runs once per day at the configured time
        log.info("Auto-backup scheduler started");
    }

    /** Schedule the next run at the configured time of day (default 22:05). */
    private void scheduleDaily() {
        if (scheduler == null || scheduler.isShutdown()) return;
        LocalTime target = configuredBackupTime();
        long delayMs = millisUntil(target);
        try {
            scheduler.schedule(this::runAutoBackup, delayMs, TimeUnit.MILLISECONDS);
            log.info("Next automatic backup at {} (in {} minutes)", target, delayMs / 60000);
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private void runAutoBackup() {
        try {
            if (configService.getBool("backup.auto.enabled", true)) {
                backupService.autoBackupTick(configService.getInt("backup.auto.keep", 20));
            }
        } catch (Throwable t) { // never let an exception cancel the recurring schedule
            log.warn("Auto backup run error: {}", t.toString());
        } finally {
            scheduleDaily(); // re-arm for the next day (picks up any config change)
        }
    }

    private LocalTime configuredBackupTime() {
        try {
            return LocalTime.parse(configService.get("backup.auto.time", "22:05").trim());
        } catch (RuntimeException e) {
            log.warn("Invalid backup.auto.time; using {}", DEFAULT_BACKUP_TIME);
            return DEFAULT_BACKUP_TIME;
        }
    }

    private long millisUntil(LocalTime target) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(target);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Math.max(1000L, Duration.between(now, next).toMillis());
    }

    private void stopAutoBackup() {
        if (scheduler == null) return;
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS))
                log.warn("Auto-backup scheduler did not stop within 10s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scheduler = null;
    }

    private void seedDefaultAdminUser() {
        try {
            UserService us = new UserService();
            if (us.countUsers() == 0) {
                us.createUser("admin", "admin", "PKI Administrator", "admin@pki.local", com.macmario.services.pki.entity.PkiUser.Role.ADMIN);
                log.info("Default admin user created (admin / admin) — change password immediately!");
            }
        } catch (SQLException e) {
            log.warn("Could not seed admin user: {}", e.getMessage());
        }
    }

    private void seedIfAbsent(Connection c, String key, String value, String desc) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT cfg_key FROM PKI_CONFIGURATION WHERE cfg_key=?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO PKI_CONFIGURATION(cfg_key,cfg_value,description) VALUES(?,?,?)")) {
                    ins.setString(1, key);
                    ins.setString(2, value);
                    ins.setString(3, desc);
                    ins.executeUpdate();
                }
            }
        }
    }
}
