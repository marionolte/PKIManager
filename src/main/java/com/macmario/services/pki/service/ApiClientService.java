package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.ApiClient;
import com.macmario.services.pki.util.EntityManagerProvider;

import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class ApiClientService {

    public List<ApiClient> findAll() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT a.*, ca.display_name AS ca_name FROM API_CLIENT a " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=a.default_ca_id ORDER BY a.name")) {
            return mapList(ps.executeQuery());
        }
    }

    public Optional<ApiClient> findById(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT a.*, ca.display_name AS ca_name FROM API_CLIENT a " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=a.default_ca_id WHERE a.id=?")) {
            ps.setLong(1, id);
            List<ApiClient> l = mapList(ps.executeQuery());
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    public Optional<ApiClient> findByApiKey(String apiKey) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT a.*, ca.display_name AS ca_name FROM API_CLIENT a " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=a.default_ca_id WHERE a.api_key=?")) {
            ps.setString(1, apiKey);
            List<ApiClient> l = mapList(ps.executeQuery());
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    public ApiClient create(String name, String description, Long defaultCaId) throws SQLException {
        String key = generateApiKey();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO API_CLIENT(name,api_key,description,active,default_ca_id,created_at) VALUES(?,?,?,TRUE,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, key);
            ps.setString(3, description);
            if (defaultCaId != null) ps.setLong(4, defaultCaId); else ps.setNull(4, Types.BIGINT);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) throw new RuntimeException("No generated key");
            ApiClient a = new ApiClient();
            a.setId(keys.getLong(1));
            a.setName(name.trim());
            a.setApiKey(key);
            a.setDescription(description);
            a.setActive(true);
            a.setDefaultCaId(defaultCaId);
            return a;
        }
    }

    public void update(Long id, String name, String description, Long defaultCaId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE API_CLIENT SET name=?,description=?,default_ca_id=? WHERE id=?")) {
            ps.setString(1, name.trim());
            ps.setString(2, description);
            if (defaultCaId != null) ps.setLong(3, defaultCaId); else ps.setNull(3, Types.BIGINT);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public void setActive(Long id, boolean active) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE API_CLIENT SET active=? WHERE id=?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public String rotateApiKey(Long id) throws SQLException {
        String newKey = generateApiKey();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE API_CLIENT SET api_key=? WHERE id=?")) {
            ps.setString(1, newKey);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        return newKey;
    }

    public void delete(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM API_CLIENT WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public long countCerts(Long apiClientId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM CERTIFICATE_RECORD WHERE api_client_id=?")) {
            ps.setLong(1, apiClientId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "pki_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<ApiClient> mapList(ResultSet rs) throws SQLException {
        List<ApiClient> list = new ArrayList<>();
        while (rs.next()) {
            ApiClient a = new ApiClient();
            a.setId(rs.getLong("id"));
            a.setName(rs.getString("name"));
            a.setApiKey(rs.getString("api_key"));
            a.setDescription(rs.getString("description"));
            a.setActive(rs.getBoolean("active"));
            long dcaId = rs.getLong("default_ca_id");
            if (!rs.wasNull()) a.setDefaultCaId(dcaId);
            a.setDefaultCaName(rs.getString("ca_name"));
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) a.setCreatedAt(ca.toLocalDateTime());
            list.add(a);
        }
        return list;
    }
}
