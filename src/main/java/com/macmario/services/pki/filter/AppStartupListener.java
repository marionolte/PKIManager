package com.macmario.services.pki.filter;

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
import java.util.Enumeration;

@WebListener
public class AppStartupListener implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(AppStartupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("=== MH Service PKI Manager starting ===");
        try {
            EntityManagerProvider.init();
            seedDefaultConfig();
            seedDefaultAdminUser();
            log.info("=== PKI Manager ready ===");
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            throw new RuntimeException("PKI Manager startup failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("=== PKI Manager shutting down ===");
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
        } catch (SQLException e) {
            log.warn("Could not seed config: {}", e.getMessage());
        }
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
