package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.CsrRequest;
import com.macmario.services.pki.entity.CertificateRecord;
import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.util.EntityManagerProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.io.StringReader;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CsrRequestService {

    public CsrRequest submitForApiClient(String csrPem, String requesterName, String requesterEmail,
                                          String notes, Long apiClientId) throws SQLException {
        CsrRequest r = submit(csrPem, requesterName, requesterEmail, notes);
        if (apiClientId != null) {
            try (Connection c = EntityManagerProvider.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE CSR_REQUEST SET api_client_id=? WHERE id=?")) {
                ps.setLong(1, apiClientId);
                ps.setLong(2, r.getId());
                ps.executeUpdate();
            }
            r.setApiClientId(apiClientId);
        }
        return r;
    }

    public List<CsrRequest> findByApiClient(Long apiClientId) throws SQLException {
        List<CsrRequest> list = new ArrayList<>();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT r.*, ca.display_name as ca_name FROM CSR_REQUEST r " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=r.signed_ca_id " +
                 "WHERE r.api_client_id=? ORDER BY r.requested_at DESC")) {
            ps.setLong(1, apiClientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public CsrRequest submit(String csrPem, String requesterName, String requesterEmail, String notes) throws SQLException {
        String cn = extractCn(csrPem);
        String token = UUID.randomUUID().toString();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO CSR_REQUEST(tracking_token,csr_pem,subject_cn,requester_name,requester_email,requester_notes,status)" +
                 " VALUES(?,?,?,?,?,?,'PENDING')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, token);
            ps.setString(2, csrPem.trim());
            ps.setString(3, cn);
            ps.setString(4, requesterName);
            ps.setString(5, requesterEmail);
            ps.setString(6, notes);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) throw new RuntimeException("No generated key");
            CsrRequest r = new CsrRequest();
            r.setId(keys.getLong(1));
            r.setTrackingToken(token);
            r.setCsrPem(csrPem.trim());
            r.setSubjectCn(cn);
            r.setRequesterName(requesterName);
            r.setRequesterEmail(requesterEmail);
            r.setRequesterNotes(notes);
            return r;
        }
    }

    public List<CsrRequest> findByStatus(CsrRequest.Status status) throws SQLException {
        List<CsrRequest> list = new ArrayList<>();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT r.*, ca.display_name as ca_name FROM CSR_REQUEST r " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=r.signed_ca_id " +
                 "WHERE r.status=? ORDER BY r.requested_at DESC")) {
            ps.setString(1, status.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<CsrRequest> findAll() throws SQLException {
        List<CsrRequest> list = new ArrayList<>();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT r.*, ca.display_name as ca_name FROM CSR_REQUEST r " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=r.signed_ca_id " +
                 "ORDER BY r.requested_at DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Optional<CsrRequest> findByToken(String token) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT r.*, ca.display_name as ca_name FROM CSR_REQUEST r " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=r.signed_ca_id " +
                 "WHERE r.tracking_token=?")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    public Optional<CsrRequest> findById(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT r.*, ca.display_name as ca_name FROM CSR_REQUEST r " +
                 "LEFT JOIN CA_CONFIG ca ON ca.id=r.signed_ca_id " +
                 "WHERE r.id=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    public long countPending() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM CSR_REQUEST WHERE status='PENDING'")) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public void sign(Long requestId, Long certId, Long caId, String adminNotes) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE CSR_REQUEST SET status='SIGNED',processed_at=?,signed_cert_id=?,signed_ca_id=?,admin_notes=? WHERE id=?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(2, certId);
            ps.setLong(3, caId);
            ps.setString(4, adminNotes);
            ps.setLong(5, requestId);
            ps.executeUpdate();
        }
    }

    public void reject(Long requestId, String adminNotes) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE CSR_REQUEST SET status='REJECTED',processed_at=?,admin_notes=? WHERE id=?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, adminNotes);
            ps.setLong(3, requestId);
            ps.executeUpdate();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String extractCn(String csrPem) {
        try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
            Object obj = parser.readObject();
            if (obj instanceof PKCS10CertificationRequest csr) {
                String subject = csr.getSubject().toString();
                for (String part : subject.split(",")) {
                    part = part.trim();
                    if (part.startsWith("CN=")) return part.substring(3);
                }
                return subject;
            }
        } catch (IOException ignored) {}
        return "Unknown";
    }

    private CsrRequest map(ResultSet rs) throws SQLException {
        CsrRequest r = new CsrRequest();
        r.setId(rs.getLong("id"));
        r.setTrackingToken(rs.getString("tracking_token"));
        r.setCsrPem(rs.getString("csr_pem"));
        r.setSubjectCn(rs.getString("subject_cn"));
        r.setRequesterName(rs.getString("requester_name"));
        r.setRequesterEmail(rs.getString("requester_email"));
        r.setRequesterNotes(rs.getString("requester_notes"));
        r.setStatus(CsrRequest.Status.valueOf(rs.getString("status")));
        Timestamp reqAt = rs.getTimestamp("requested_at");
        if (reqAt != null) r.setRequestedAt(reqAt.toLocalDateTime());
        Timestamp procAt = rs.getTimestamp("processed_at");
        if (procAt != null) r.setProcessedAt(procAt.toLocalDateTime());
        long certId = rs.getLong("signed_cert_id"); if (!rs.wasNull()) r.setSignedCertId(certId);
        long caId   = rs.getLong("signed_ca_id");   if (!rs.wasNull()) r.setSignedCaId(caId);
        r.setAdminNotes(rs.getString("admin_notes"));
        try { r.setIssuingCaName(rs.getString("ca_name")); } catch (SQLException ignored) {}
        try { long acId = rs.getLong("api_client_id"); if (!rs.wasNull()) r.setApiClientId(acId); }
        catch (SQLException ignored) {}
        return r;
    }
}
