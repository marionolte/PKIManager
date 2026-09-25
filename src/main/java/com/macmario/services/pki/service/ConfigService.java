package com.macmario.services.pki.service;

import com.macmario.services.pki.util.EntityManagerProvider;

import java.sql.*;
import java.util.Optional;

/** Read/write access to the PKI_CONFIGURATION key-value settings table. */
public class ConfigService {

    public Optional<String> get(String key) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT cfg_value FROM PKI_CONFIGURATION WHERE cfg_key=?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
        }
    }

    public String get(String key, String def) {
        try { return get(key).orElse(def); }
        catch (SQLException e) { return def; }
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def)).trim()); }
        catch (RuntimeException e) { return def; }
    }

    public boolean getBool(String key, boolean def) {
        String v = get(key, String.valueOf(def));
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    /** Upsert a configuration value. */
    public void set(String key, String value) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE PKI_CONFIGURATION SET cfg_value=? WHERE cfg_key=?")) {
                ps.setString(1, value);
                ps.setString(2, key);
                if (ps.executeUpdate() > 0) return;
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO PKI_CONFIGURATION(cfg_key,cfg_value,description) VALUES(?,?,?)")) {
                ins.setString(1, key);
                ins.setString(2, value);
                ins.setString(3, "");
                ins.executeUpdate();
            }
        }
    }
}
