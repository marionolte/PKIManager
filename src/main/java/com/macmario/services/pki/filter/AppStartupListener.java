package com.macmario.services.pki.filter;

import com.macmario.services.pki.util.EntityManagerProvider;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebListener
public class AppStartupListener implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(AppStartupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("=== MHService PKI Manager starting ===");
        try {
            EntityManagerProvider.init();
            seedDefaultConfig();
            log.info("=== PKI Manager ready ===");
        } catch (Exception e) {
            log.error("Startup failed", e);
            throw new RuntimeException("PKI Manager startup failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("=== PKI Manager shutting down ===");
        EntityManagerProvider.close();
    }

    private void seedDefaultConfig() {
        try (Connection c = EntityManagerProvider.getConnection()) {
            seedIfAbsent(c, "global.role", "master,subRoot1", "Active CA roles (pki.conf [global] role=)");
            seedIfAbsent(c, "crl.validity.days", "30", "CRL validity in days");
            seedIfAbsent(c, "cert.expiry.warn.days", "30", "Days before expiry to warn");
            seedIfAbsent(c, "org.name", "MHService", "Organisation name");
        } catch (Exception e) {
            log.warn("Could not seed config: {}", e.getMessage());
        }
    }

    private void seedIfAbsent(Connection c, String key, String value, String desc) throws Exception {
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
