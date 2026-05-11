package com.macmario.services.pki.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

/**
 * Manages the H2 JDBC connection.
 * The DB file is written to ${catalina.base}/pki-data/ when running under Tomcat,
 * or ./pki-data/ otherwise.
 */
public class EntityManagerProvider {

    private static final Logger log = LoggerFactory.getLogger(EntityManagerProvider.class);
    private static String DB_URL;
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        try {
            Class.forName("org.h2.Driver");

            // Resolve data directory: prefer ${catalina.base}, fall back to CWD
            String catBase = System.getProperty("catalina.base");
            Path dataDir = catBase != null
                    ? Paths.get(catBase, "pki-data")
                    : Paths.get(System.getProperty("user.dir"), "pki-data");

            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                log.info("Created PKI data dir: {}", dataDir.toAbsolutePath());
            }

            DB_URL = "jdbc:h2:" + dataDir.toAbsolutePath().resolve("pki-db")
                   + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";

            log.info("H2 URL: {}", DB_URL);

            try (Connection c = DriverManager.getConnection(DB_URL, "sa", "")) {
                createSchema(c);
            }
            initialized = true;
            log.info("H2 database initialised at {}", dataDir.toAbsolutePath());
        } catch (ClassNotFoundException | IOException | SQLException e) {
            throw new RuntimeException("DB init failed", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized) init();
        return DriverManager.getConnection(DB_URL, "sa", "");
    }

    public static synchronized void close() {
        if (!initialized) return;
        initialized = false;
        if (DB_URL == null) return;
        try (Connection c = DriverManager.getConnection(DB_URL, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
            log.info("H2 database shut down cleanly.");
        } catch (SQLException e) {
            log.warn("H2 SHUTDOWN error (may already be closed): {}", e.getMessage());
        }
        DB_URL = null;
    }

    private static void createSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {

            st.execute(
                "CREATE TABLE IF NOT EXISTS CA_CONFIG (" +
                "  id              BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  role_name       VARCHAR(100) NOT NULL UNIQUE," +
                "  display_name    VARCHAR(200) NOT NULL," +
                "  ca_type         VARCHAR(20)  NOT NULL DEFAULT 'ROOT'," +
                "  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'," +
                "  parent_ca_id    BIGINT," +
                "  country         VARCHAR(3)," +
                "  state           VARCHAR(100)," +
                "  locality        VARCHAR(100)," +
                "  organization    VARCHAR(200)," +
                "  org_unit        VARCHAR(200)," +
                "  common_name     VARCHAR(200) NOT NULL," +
                "  email_address   VARCHAR(200)," +
                "  default_days    INT          NOT NULL DEFAULT 730," +
                "  default_md      VARCHAR(20)  DEFAULT 'sha256'," +
                "  key_size        INT          NOT NULL DEFAULT 4096," +
                "  crl_url         VARCHAR(500)," +
                "  ocsp_url        VARCHAR(500)," +
                "  certificate_pem CLOB," +
                "  private_key_pem CLOB," +
                "  serial_number   VARCHAR(50)," +
                "  valid_from      TIMESTAMP," +
                "  valid_until     TIMESTAMP," +
                "  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  updated_at      TIMESTAMP," +
                "  FOREIGN KEY (parent_ca_id) REFERENCES CA_CONFIG(id)" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS CERTIFICATE_RECORD (" +
                "  id                  BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  issuing_ca_id       BIGINT NOT NULL," +
                "  serial_number       VARCHAR(50) NOT NULL UNIQUE," +
                "  cert_status         VARCHAR(20) NOT NULL DEFAULT 'VALID'," +
                "  cert_type           VARCHAR(30) NOT NULL DEFAULT 'SERVER'," +
                "  country             VARCHAR(3)," +
                "  state               VARCHAR(100)," +
                "  locality            VARCHAR(100)," +
                "  organization        VARCHAR(200)," +
                "  org_unit            VARCHAR(200)," +
                "  common_name         VARCHAR(300) NOT NULL," +
                "  email_address       VARCHAR(200)," +
                "  san_dns             CLOB," +
                "  san_ip              VARCHAR(500)," +
                "  valid_from          TIMESTAMP NOT NULL," +
                "  valid_until         TIMESTAMP NOT NULL," +
                "  key_size            INT NOT NULL DEFAULT 4096," +
                "  signature_algorithm VARCHAR(50) DEFAULT 'SHA256withRSA'," +
                "  certificate_pem     CLOB," +
                "  csr_pem             CLOB," +
                "  private_key_pem     CLOB," +
                "  fingerprint_sha256  VARCHAR(100)," +
                "  issued_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  requester           VARCHAR(200)," +
                "  notes               CLOB," +
                "  FOREIGN KEY (issuing_ca_id) REFERENCES CA_CONFIG(id)" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS REVOKED_CERTIFICATE (" +
                "  id             BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  certificate_id BIGINT NOT NULL," +
                "  serial_number  VARCHAR(50) NOT NULL," +
                "  revoked_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  reason         VARCHAR(50) NOT NULL DEFAULT 'UNSPECIFIED'," +
                "  revoked_by     VARCHAR(200)," +
                "  comment        CLOB," +
                "  FOREIGN KEY (certificate_id) REFERENCES CERTIFICATE_RECORD(id)" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS PKI_CONFIGURATION (" +
                "  cfg_key     VARCHAR(100) PRIMARY KEY," +
                "  cfg_value   CLOB," +
                "  description VARCHAR(500)," +
                "  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS PKI_USER (" +
                "  id            BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  username      VARCHAR(100) NOT NULL UNIQUE," +
                "  password_hash VARCHAR(200) NOT NULL," +
                "  salt          VARCHAR(100) NOT NULL," +
                "  display_name  VARCHAR(200)," +
                "  email         VARCHAR(200)," +
                "  role          VARCHAR(20) NOT NULL DEFAULT 'VIEWER'," +
                "  active        BOOLEAN NOT NULL DEFAULT TRUE," +
                "  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  last_login_at TIMESTAMP" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS CSR_REQUEST (" +
                "  id               BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  tracking_token   VARCHAR(36) NOT NULL UNIQUE," +
                "  csr_pem          CLOB NOT NULL," +
                "  subject_cn       VARCHAR(300)," +
                "  requester_name   VARCHAR(200)," +
                "  requester_email  VARCHAR(200)," +
                "  requester_notes  CLOB," +
                "  status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                "  requested_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  processed_at     TIMESTAMP," +
                "  signed_cert_id   BIGINT," +
                "  signed_ca_id     BIGINT," +
                "  admin_notes      CLOB" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS ACME_CERTIFICATE (" +
                "  id               BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  domain           VARCHAR(253) NOT NULL UNIQUE," +
                "  account_url      VARCHAR(500)," +
                "  account_key_pem  CLOB," +
                "  cert_pem         CLOB," +
                "  chain_pem        CLOB," +
                "  private_key_pem  CLOB," +
                "  valid_from       TIMESTAMP," +
                "  valid_until      TIMESTAMP," +
                "  auto_renew       BOOLEAN NOT NULL DEFAULT TRUE," +
                "  last_renewed_at  TIMESTAMP," +
                "  status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                "  error_message    CLOB," +
                "  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS ACME_CHALLENGE_TOKEN (" +
                "  token       VARCHAR(100) PRIMARY KEY," +
                "  key_auth    VARCHAR(500) NOT NULL," +
                "  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // Idempotent column additions for existing installs
            try { st.execute("ALTER TABLE CERTIFICATE_RECORD ADD COLUMN IF NOT EXISTS download_token VARCHAR(36)"); }
            catch (Exception ignored) {}
            try { st.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_cert_download_token ON CERTIFICATE_RECORD(download_token)"); }
            catch (Exception ignored) {}

            log.info("Database schema ready.");
        }
    }
}
