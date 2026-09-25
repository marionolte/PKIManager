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

    private static final String SELECT =
        "SELECT a.*, ca.display_name AS ca_name, u.username AS owner_name " +
        "FROM API_CLIENT a " +
        "LEFT JOIN CA_CONFIG ca ON ca.id=a.default_ca_id " +
        "LEFT JOIN PKI_USER u ON u.id=a.owner_user_id ";

    public List<ApiClient> findAll() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY a.name")) {
            return mapList(ps.executeQuery());
        }
    }

    public Optional<ApiClient> findById(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE a.id=?")) {
            ps.setLong(1, id);
            List<ApiClient> l = mapList(ps.executeQuery());
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    /** Clients owned by a given user (their own requests). */
    public List<ApiClient> findByOwner(Long ownerUserId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE a.owner_user_id=? ORDER BY a.name")) {
            ps.setLong(1, ownerUserId);
            return mapList(ps.executeQuery());
        }
    }

    /** A single client, but only if it belongs to {@code ownerUserId} (IDOR guard). */
    public Optional<ApiClient> findOwned(Long id, Long ownerUserId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE a.id=? AND a.owner_user_id=?")) {
            ps.setLong(1, id);
            ps.setLong(2, ownerUserId);
            List<ApiClient> l = mapList(ps.executeQuery());
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    public List<ApiClient> findPending() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE a.approval_status='PENDING' ORDER BY a.created_at")) {
            return mapList(ps.executeQuery());
        }
    }

    public long countPending() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM API_CLIENT WHERE approval_status='PENDING'")) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public long countPendingByOwner(Long ownerUserId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM API_CLIENT WHERE owner_user_id=? AND approval_status='PENDING'")) {
            ps.setLong(1, ownerUserId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /**
     * The single gate the API authentication filter uses: a client authenticates only
     * when it is active, APPROVED, and either has no owner or its owner is still active.
     */
    public Optional<ApiClient> authenticate(String apiKey) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT +
                 "WHERE a.api_key=? AND a.active=TRUE " +
                 "AND (a.approval_status='APPROVED' OR a.approval_status IS NULL) " +
                 "AND (a.owner_user_id IS NULL OR u.active=TRUE)")) {
            ps.setString(1, apiKey);
            List<ApiClient> l = mapList(ps.executeQuery());
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    /** Admin direct-create: immediately APPROVED and active. */
    public ApiClient create(String name, String description, Long defaultCaId, Long ownerUserId) throws SQLException {
        return insert(name, description, defaultCaId, ownerUserId, ApiClient.ApprovalStatus.APPROVED, true);
    }

    /** User self-service request: PENDING and inactive until an admin approves it. */
    public ApiClient request(String name, String description, Long defaultCaId, Long ownerUserId) throws SQLException {
        return insert(name, description, defaultCaId, ownerUserId, ApiClient.ApprovalStatus.PENDING, false);
    }

    private ApiClient insert(String name, String description, Long defaultCaId, Long ownerUserId,
                             ApiClient.ApprovalStatus status, boolean active) throws SQLException {
        String key = generateApiKey();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO API_CLIENT(name,api_key,description,active,default_ca_id,created_at,owner_user_id,approval_status) " +
                 "VALUES(?,?,?,?,?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, key);
            ps.setString(3, description);
            ps.setBoolean(4, active);
            if (defaultCaId != null) ps.setLong(5, defaultCaId); else ps.setNull(5, Types.BIGINT);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            if (ownerUserId != null) ps.setLong(7, ownerUserId); else ps.setNull(7, Types.BIGINT);
            ps.setString(8, status.name());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) throw new RuntimeException("No generated key");
            ApiClient a = new ApiClient();
            a.setId(keys.getLong(1));
            a.setName(name.trim());
            a.setApiKey(key);
            a.setDescription(description);
            a.setActive(active);
            a.setDefaultCaId(defaultCaId);
            a.setOwnerUserId(ownerUserId);
            a.setApprovalStatus(status);
            return a;
        }
    }

    public void approve(Long id, String adminUsername) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE API_CLIENT SET approval_status='APPROVED', active=TRUE, approved_by=? WHERE id=?")) {
            ps.setString(1, adminUsername);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void reject(Long id, String adminUsername) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE API_CLIENT SET approval_status='REJECTED', active=FALSE, approved_by=? WHERE id=?")) {
            ps.setString(1, adminUsername);
            ps.setLong(2, id);
            ps.executeUpdate();
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
        // Enabling is only allowed for APPROVED clients — never activate a pending/rejected one.
        if (active) {
            ApiClient existing = findById(id).orElseThrow(() -> new IllegalArgumentException("API client not found: " + id));
            if (!existing.isApproved())
                throw new IllegalStateException("API client must be approved before it can be enabled");
        }
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

    /** Record the most recent authenticated API access (client IP, user-agent, timestamp). */
    public void recordAccess(Long id, String ip, String userAgent) throws SQLException {
        String safeIp = truncate(ip, 64);
        String safeUa = truncate(userAgent, 512);
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE API_CLIENT SET last_used_at=?, last_ip=?, last_user_agent=? WHERE id=?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, safeIp);
            ps.setString(3, safeUa);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
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
            Timestamp lu = rs.getTimestamp("last_used_at");
            if (lu != null) a.setLastUsedAt(lu.toLocalDateTime());
            a.setLastIp(rs.getString("last_ip"));
            a.setLastUserAgent(rs.getString("last_user_agent"));
            long owner = rs.getLong("owner_user_id");
            if (!rs.wasNull()) a.setOwnerUserId(owner);
            a.setOwnerName(rs.getString("owner_name"));
            String st = rs.getString("approval_status");
            a.setApprovalStatus(st == null ? ApiClient.ApprovalStatus.APPROVED : ApiClient.ApprovalStatus.valueOf(st));
            a.setApprovedBy(rs.getString("approved_by"));
            list.add(a);
        }
        return list;
    }
}
