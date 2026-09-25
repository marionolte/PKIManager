package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.util.EntityManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CaService {
    private static final Logger log = LoggerFactory.getLogger(CaService.class);
    private final PkiCryptoService crypto = new PkiCryptoService();

    public List<CaConfig> findAll() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT a.*, b.display_name AS parent_name FROM CA_CONFIG a " +
                "LEFT JOIN CA_CONFIG b ON a.parent_ca_id = b.id ORDER BY a.ca_type, a.role_name")) {
            return mapList(ps.executeQuery());
        }
    }

    public Optional<CaConfig> findById(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT a.*, b.display_name AS parent_name FROM CA_CONFIG a " +
                "LEFT JOIN CA_CONFIG b ON a.parent_ca_id = b.id WHERE a.id = ?")) {
            ps.setLong(1, id);
            List<CaConfig> list = mapList(ps.executeQuery());
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public List<CaConfig> findChildren(Long parentId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT a.*, b.display_name AS parent_name FROM CA_CONFIG a " +
                "LEFT JOIN CA_CONFIG b ON a.parent_ca_id = b.id WHERE a.parent_ca_id = ? ORDER BY a.role_name")) {
            ps.setLong(1, parentId);
            return mapList(ps.executeQuery());
        }
    }

    public long countIssuedCerts(Long caId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM CERTIFICATE_RECORD WHERE issuing_ca_id = ?")) {
            ps.setLong(1, caId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public CaConfig createRootCa(CaConfig ca) throws GeneralSecurityException, OperatorCreationException, IOException, SQLException {
        ca.setCaType(CaConfig.CaType.ROOT);
        ca.setParentCaId(null);
        ca.setPermittedDomains(null); // Name Constraints apply to sub/issuing CAs only
        crypto.initRootCa(ca);
        return persist(ca);
    }

    public CaConfig createSubCa(CaConfig ca, Long parentCaId) throws GeneralSecurityException, OperatorCreationException, IOException, SQLException {
        CaConfig parent = findById(parentCaId)
            .orElseThrow(() -> new IllegalArgumentException("Parent CA not found: " + parentCaId));
        requireActiveParent(parent);
        ca.setCaType(CaConfig.CaType.INTERMEDIATE);
        ca.setParentCaId(parent.getId());
        crypto.initSubCa(ca, parent);
        return persist(ca);
    }

    public CaConfig createIssuingCa(CaConfig ca, Long parentCaId) throws GeneralSecurityException, OperatorCreationException, IOException, SQLException {
        CaConfig parent = findById(parentCaId)
            .orElseThrow(() -> new IllegalArgumentException("Parent CA not found: " + parentCaId));
        requireActiveParent(parent);
        ca.setCaType(CaConfig.CaType.ISSUING);
        ca.setParentCaId(parent.getId());
        crypto.initSubCa(ca, parent);
        return persist(ca);
    }

    /**
     * Import an existing Root or Sub CA from a PEM certificate + private key rather
     * than generating a new key pair. Metadata (subject, serial, validity, key size,
     * Name Constraints) is taken from the certificate; role/display name and default
     * issuance settings come from {@code ca}. For a non-root import a parent CA may be
     * selected ({@code parentCaId}); if none is given the sub CA is stored as an orphan.
     */
    public CaConfig importCa(CaConfig ca, String certPem, String keyPem, String keyPassword, Long parentCaId)
            throws GeneralSecurityException, OperatorCreationException, IOException, SQLException {
        if (ca.getRoleName() == null || ca.getRoleName().isBlank())
            throw new IllegalArgumentException("Role name is required");
        if (ca.getDisplayName() == null || ca.getDisplayName().isBlank())
            throw new IllegalArgumentException("Display name is required");
        if (ca.getCaType() == null)
            throw new IllegalArgumentException("CA type is required");

        String parentCertPem = null;
        if (ca.getCaType() != CaConfig.CaType.ROOT && parentCaId != null) {
            CaConfig parent = findById(parentCaId)
                .orElseThrow(() -> new IllegalArgumentException("Parent CA not found: " + parentCaId));
            ca.setParentCaId(parent.getId());
            parentCertPem = parent.getCertificatePem();
        } else {
            ca.setParentCaId(null);
        }

        crypto.importCa(ca, certPem, keyPem, keyPassword, parentCertPem);
        ca.setStatus(CaConfig.CaStatus.ACTIVE);
        return persist(ca);
    }

    public void disable(Long id) throws SQLException {
        requireNotRevoked(id);
        updateStatus(id, CaConfig.CaStatus.DISABLED);
    }

    public void enable(Long id) throws SQLException {
        requireNotRevoked(id);
        updateStatus(id, CaConfig.CaStatus.ACTIVE);
    }

    private void requireActiveParent(CaConfig parent) {
        if (parent.getStatus() != CaConfig.CaStatus.ACTIVE)
            throw new IllegalArgumentException(
                "Parent CA '" + parent.getDisplayName() + "' is " + parent.getStatus()
                + " and cannot issue new sub CAs");
    }

    private void requireNotRevoked(Long id) throws SQLException {
        CaConfig ca = findById(id)
            .orElseThrow(() -> new IllegalArgumentException("CA not found: " + id));
        if (ca.getStatus() == CaConfig.CaStatus.REVOKED)
            throw new IllegalArgumentException("CA is revoked; revocation is permanent");
    }

    private void updateStatus(Long id, CaConfig.CaStatus status) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE CA_CONFIG SET status=?, updated_at=? WHERE id=?")) {
            ps.setString(1, status.name());
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Revoke a CA (Root or Sub). Marks the CA and every descendant CA REVOKED,
     * and revokes all still-VALID certificates issued by any of them. Runs in a
     * single transaction. Revocation is permanent.
     */
    public void revoke(Long id, String reason, String revokedBy, String comment) throws SQLException {
        String safeReason = (reason == null || reason.isBlank()) ? "UNSPECIFIED" : reason;
        try (Connection c = EntityManagerProvider.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<Long> caIds = collectSubtree(c, id);
                Timestamp nowTs = Timestamp.valueOf(LocalDateTime.now());
                for (Long caId : caIds) {
                    revokeCertsForCa(c, caId, safeReason, revokedBy, comment, nowTs);
                    try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE CA_CONFIG SET status='REVOKED', revoked_at=?, revocation_reason=?, " +
                        "revoked_by=?, updated_at=? WHERE id=?")) {
                        ps.setTimestamp(1, nowTs);
                        ps.setString(2, safeReason);
                        ps.setString(3, revokedBy);
                        ps.setTimestamp(4, nowTs);
                        ps.setLong(5, caId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                log.info("CA {} (+{} descendants) revoked by {} reason={}",
                        id, caIds.size() - 1, revokedBy, safeReason);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Permanently delete a CA (Root or Sub) and everything below it: all
     * descendant CAs, every certificate they issued (plus revocation records),
     * and any references from CSR requests / API clients. Destructive and
     * irreversible. Runs in a single transaction.
     */
    public void delete(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<Long> caIds = collectSubtree(c, id);
                for (Long caId : caIds) {
                    // 1. Delete revocation records for certs issued by this CA
                    exec(c, "DELETE FROM REVOKED_CERTIFICATE WHERE certificate_id IN " +
                            "(SELECT id FROM CERTIFICATE_RECORD WHERE issuing_ca_id=?)", caId);
                    // 2. Detach CSR requests that reference those certs / this CA
                    exec(c, "UPDATE CSR_REQUEST SET signed_cert_id=NULL WHERE signed_cert_id IN " +
                            "(SELECT id FROM CERTIFICATE_RECORD WHERE issuing_ca_id=?)", caId);
                    exec(c, "UPDATE CSR_REQUEST SET signed_ca_id=NULL WHERE signed_ca_id=?", caId);
                    // 3. Delete the issued certificates
                    exec(c, "DELETE FROM CERTIFICATE_RECORD WHERE issuing_ca_id=?", caId);
                    // 4. Detach API clients that default to this CA (FK)
                    exec(c, "UPDATE API_CLIENT SET default_ca_id=NULL WHERE default_ca_id=?", caId);
                }
                // 5. Delete the CAs children-first so the self-referencing FK holds
                for (int i = caIds.size() - 1; i >= 0; i--) {
                    exec(c, "DELETE FROM CA_CONFIG WHERE id=?", caIds.get(i));
                }
                c.commit();
                log.info("CA {} (+{} descendants) permanently deleted", id, caIds.size() - 1);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Return the CA id plus all descendant CA ids, parents before children. */
    private List<Long> collectSubtree(Connection c, Long rootId) throws SQLException {
        if (findById(rootId).isEmpty())
            throw new IllegalArgumentException("CA not found: " + rootId);
        List<Long> ordered = new ArrayList<>();
        List<Long> frontier = new ArrayList<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            Long current = frontier.remove(0);
            if (ordered.contains(current)) continue; // guard against cycles
            ordered.add(current);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM CA_CONFIG WHERE parent_ca_id=?")) {
                ps.setLong(1, current);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) frontier.add(rs.getLong(1));
            }
        }
        return ordered;
    }

    private void revokeCertsForCa(Connection c, Long caId, String reason, String revokedBy,
                                  String comment, Timestamp nowTs) throws SQLException {
        List<String> serials = new ArrayList<>();
        List<Long> certIds = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, serial_number FROM CERTIFICATE_RECORD WHERE issuing_ca_id=? AND cert_status='VALID'")) {
            ps.setLong(1, caId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { certIds.add(rs.getLong(1)); serials.add(rs.getString(2)); }
        }
        for (int i = 0; i < certIds.size(); i++) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE CERTIFICATE_RECORD SET cert_status='REVOKED' WHERE id=?")) {
                ps.setLong(1, certIds.get(i));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO REVOKED_CERTIFICATE(certificate_id,serial_number,revoked_at,reason,revoked_by,comment) " +
                    "VALUES(?,?,?,?,?,?)")) {
                ps.setLong(1, certIds.get(i));
                ps.setString(2, serials.get(i));
                ps.setTimestamp(3, nowTs);
                ps.setString(4, reason);
                ps.setString(5, revokedBy);
                ps.setString(6, comment != null ? comment : "Issuing CA revoked");
                ps.executeUpdate();
            }
        }
    }

    private void exec(Connection c, String sql, Long param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param);
            ps.executeUpdate();
        }
    }

    private CaConfig persist(CaConfig ca) throws SQLException {
        String sql = """
            INSERT INTO CA_CONFIG (role_name,display_name,ca_type,status,parent_ca_id,
                country,state,locality,organization,org_unit,common_name,email_address,
                default_days,default_md,key_size,crl_url,ocsp_url,permitted_domains,
                certificate_pem,private_key_pem,serial_number,valid_from,valid_until,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ca.getRoleName());
            ps.setString(2, ca.getDisplayName());
            ps.setString(3, ca.getCaType().name());
            ps.setString(4, ca.getStatus().name());
            if (ca.getParentCaId() != null) ps.setLong(5, ca.getParentCaId()); else ps.setNull(5, Types.BIGINT);
            ps.setString(6, ca.getCountry());
            ps.setString(7, ca.getState());
            ps.setString(8, ca.getLocality());
            ps.setString(9, ca.getOrganization());
            ps.setString(10, ca.getOrgUnit());
            ps.setString(11, ca.getCommonName());
            ps.setString(12, ca.getEmailAddress());
            ps.setInt(13, ca.getDefaultDays());
            ps.setString(14, ca.getDefaultMd());
            ps.setInt(15, ca.getKeySize());
            ps.setString(16, ca.getCrlUrl());
            ps.setString(17, ca.getOcspUrl());
            ps.setString(18, ca.getPermittedDomains());
            ps.setString(19, ca.getCertificatePem());
            ps.setString(20, ca.getPrivateKeyPem());
            ps.setString(21, ca.getSerialNumber());
            ps.setTimestamp(22, ca.getValidFrom() != null ? Timestamp.valueOf(ca.getValidFrom()) : null);
            ps.setTimestamp(23, ca.getValidUntil() != null ? Timestamp.valueOf(ca.getValidUntil()) : null);
            ps.setTimestamp(24, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) ca.setId(keys.getLong(1));
            log.info("CA persisted: {} id={}", ca.getRoleName(), ca.getId());
            return ca;
        }
    }

    private List<CaConfig> mapList(ResultSet rs) throws SQLException {
        List<CaConfig> list = new ArrayList<>();
        while (rs.next()) {
            CaConfig ca = new CaConfig();
            ca.setId(rs.getLong("id"));
            ca.setRoleName(rs.getString("role_name"));
            ca.setDisplayName(rs.getString("display_name"));
            ca.setCaType(CaConfig.CaType.valueOf(rs.getString("ca_type")));
            ca.setStatus(CaConfig.CaStatus.valueOf(rs.getString("status")));
            long pid = rs.getLong("parent_ca_id"); if (!rs.wasNull()) ca.setParentCaId(pid);
            ca.setParentCaDisplayName(rs.getString("parent_name"));
            ca.setCountry(rs.getString("country"));
            ca.setState(rs.getString("state"));
            ca.setLocality(rs.getString("locality"));
            ca.setOrganization(rs.getString("organization"));
            ca.setOrgUnit(rs.getString("org_unit"));
            ca.setCommonName(rs.getString("common_name"));
            ca.setEmailAddress(rs.getString("email_address"));
            ca.setDefaultDays(rs.getInt("default_days"));
            ca.setDefaultMd(rs.getString("default_md"));
            ca.setKeySize(rs.getInt("key_size"));
            ca.setCrlUrl(rs.getString("crl_url"));
            ca.setOcspUrl(rs.getString("ocsp_url"));
            ca.setPermittedDomains(rs.getString("permitted_domains"));
            ca.setRevocationReason(rs.getString("revocation_reason"));
            ca.setRevokedBy(rs.getString("revoked_by"));
            Timestamp rv = rs.getTimestamp("revoked_at"); if (rv != null) ca.setRevokedAt(rv.toLocalDateTime());
            ca.setCertificatePem(rs.getString("certificate_pem"));
            ca.setPrivateKeyPem(rs.getString("private_key_pem"));
            ca.setSerialNumber(rs.getString("serial_number"));
            Timestamp vf = rs.getTimestamp("valid_from");  if (vf != null) ca.setValidFrom(vf.toLocalDateTime());
            Timestamp vu = rs.getTimestamp("valid_until"); if (vu != null) ca.setValidUntil(vu.toLocalDateTime());
            Timestamp ca2 = rs.getTimestamp("created_at"); if (ca2 != null) ca.setCreatedAt(ca2.toLocalDateTime());
            list.add(ca);
        }
        return list;
    }
}
