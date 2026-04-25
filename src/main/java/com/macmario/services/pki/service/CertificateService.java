package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.CertificateRecord;
import com.macmario.services.pki.entity.RevokedCertificate;
import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.util.EntityManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CertificateService {
    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private final PkiCryptoService crypto = new PkiCryptoService();

    public List<CertificateRecord> findAll() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, a.display_name AS ca_name FROM CERTIFICATE_RECORD r " +
                "JOIN CA_CONFIG a ON r.issuing_ca_id=a.id ORDER BY r.issued_at DESC")) {
            return mapList(ps.executeQuery());
        }
    }

    public List<CertificateRecord> findByCa(Long caId) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, a.display_name AS ca_name FROM CERTIFICATE_RECORD r " +
                "JOIN CA_CONFIG a ON r.issuing_ca_id=a.id WHERE r.issuing_ca_id=? ORDER BY r.issued_at DESC")) {
            ps.setLong(1, caId);
            return mapList(ps.executeQuery());
        }
    }

    public Optional<CertificateRecord> findById(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, a.display_name AS ca_name FROM CERTIFICATE_RECORD r " +
                "JOIN CA_CONFIG a ON r.issuing_ca_id=a.id WHERE r.id=?")) {
            ps.setLong(1, id);
            List<CertificateRecord> l = mapList(ps.executeQuery());
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    public List<CertificateRecord> findExpiringSoon(int days) throws SQLException {
        LocalDateTime threshold = LocalDateTime.now().plusDays(days);
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, a.display_name AS ca_name FROM CERTIFICATE_RECORD r " +
                "JOIN CA_CONFIG a ON r.issuing_ca_id=a.id " +
                "WHERE r.cert_status='VALID' AND r.valid_until <= ? ORDER BY r.valid_until")) {
            ps.setTimestamp(1, Timestamp.valueOf(threshold));
            return mapList(ps.executeQuery());
        }
    }

    public long countByStatus(String status) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM CERTIFICATE_RECORD WHERE cert_status=?")) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public long countTotal() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM CERTIFICATE_RECORD");
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public CertificateRecord generateAndIssue(CaConfig ca, CertificateRecord template) throws Exception {
        crypto.generateAndSign(ca, template);
        template.setIssuingCaId(ca.getId());
        template.setIssuingCaDisplayName(ca.getDisplayName());
        return persist(template);
    }

    public CertificateRecord signExternalCsr(String csrPem, CaConfig ca, CertificateRecord template) throws Exception {
        crypto.signCsr(csrPem, ca, template);
        template.setIssuingCaId(ca.getId());
        template.setIssuingCaDisplayName(ca.getDisplayName());
        return persist(template);
    }

    public void revoke(Long certId, String reason, String revokedBy, String comment) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Update cert status
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE CERTIFICATE_RECORD SET cert_status='REVOKED' WHERE id=?")) {
                    ps.setLong(1, certId);
                    ps.executeUpdate();
                }
                // Fetch serial
                String serial = "";
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT serial_number FROM CERTIFICATE_RECORD WHERE id=?")) {
                    ps.setLong(1, certId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) serial = rs.getString(1);
                }
                // Insert revocation record
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO REVOKED_CERTIFICATE(certificate_id,serial_number,revoked_at,reason,revoked_by,comment) VALUES(?,?,?,?,?,?)")) {
                    ps.setLong(1, certId);
                    ps.setString(2, serial);
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setString(4, reason);
                    ps.setString(5, revokedBy);
                    ps.setString(6, comment);
                    ps.executeUpdate();
                }
                c.commit();
                log.info("Certificate {} revoked by {}", serial, revokedBy);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private CertificateRecord persist(CertificateRecord r) throws SQLException {
        String sql = """
            INSERT INTO CERTIFICATE_RECORD
                (issuing_ca_id,serial_number,cert_status,cert_type,
                 country,state,locality,organization,org_unit,common_name,email_address,
                 san_dns,san_ip,valid_from,valid_until,key_size,signature_algorithm,
                 certificate_pem,csr_pem,private_key_pem,fingerprint_sha256,
                 issued_at,requester,notes)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.getIssuingCaId());
            ps.setString(2, r.getSerialNumber());
            ps.setString(3, r.getCertStatus().name());
            ps.setString(4, r.getCertType().name());
            ps.setString(5, r.getCountry());
            ps.setString(6, r.getState());
            ps.setString(7, r.getLocality());
            ps.setString(8, r.getOrganization());
            ps.setString(9, r.getOrgUnit());
            ps.setString(10, r.getCommonName());
            ps.setString(11, r.getEmailAddress());
            ps.setString(12, r.getSanDns());
            ps.setString(13, r.getSanIp());
            ps.setTimestamp(14, Timestamp.valueOf(r.getValidFrom()));
            ps.setTimestamp(15, Timestamp.valueOf(r.getValidUntil()));
            ps.setInt(16, r.getKeySize());
            ps.setString(17, r.getSignatureAlgorithm());
            ps.setString(18, r.getCertificatePem());
            ps.setString(19, r.getCsrPem());
            ps.setString(20, r.getPrivateKeyPem());
            ps.setString(21, r.getFingerprintSha256());
            ps.setTimestamp(22, Timestamp.valueOf(r.getIssuedAt() != null ? r.getIssuedAt() : LocalDateTime.now()));
            ps.setString(23, r.getRequester());
            ps.setString(24, r.getNotes());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) r.setId(keys.getLong(1));
            log.info("Certificate persisted: serial={} CN={}", r.getSerialNumber(), r.getCommonName());
            return r;
        }
    }

    private List<CertificateRecord> mapList(ResultSet rs) throws SQLException {
        List<CertificateRecord> list = new ArrayList<>();
        while (rs.next()) {
            CertificateRecord r = new CertificateRecord();
            r.setId(rs.getLong("id"));
            r.setIssuingCaId(rs.getLong("issuing_ca_id"));
            r.setIssuingCaDisplayName(rs.getString("ca_name"));
            r.setSerialNumber(rs.getString("serial_number"));
            r.setCertStatus(CertificateRecord.CertStatus.valueOf(rs.getString("cert_status")));
            r.setCertType(CertificateRecord.CertType.valueOf(rs.getString("cert_type")));
            r.setCountry(rs.getString("country"));
            r.setState(rs.getString("state"));
            r.setLocality(rs.getString("locality"));
            r.setOrganization(rs.getString("organization"));
            r.setOrgUnit(rs.getString("org_unit"));
            r.setCommonName(rs.getString("common_name"));
            r.setEmailAddress(rs.getString("email_address"));
            r.setSanDns(rs.getString("san_dns"));
            r.setSanIp(rs.getString("san_ip"));
            Timestamp vf = rs.getTimestamp("valid_from");  if (vf != null) r.setValidFrom(vf.toLocalDateTime());
            Timestamp vu = rs.getTimestamp("valid_until"); if (vu != null) r.setValidUntil(vu.toLocalDateTime());
            r.setKeySize(rs.getInt("key_size"));
            r.setSignatureAlgorithm(rs.getString("signature_algorithm"));
            r.setCertificatePem(rs.getString("certificate_pem"));
            r.setCsrPem(rs.getString("csr_pem"));
            r.setPrivateKeyPem(rs.getString("private_key_pem"));
            r.setFingerprintSha256(rs.getString("fingerprint_sha256"));
            Timestamp ia = rs.getTimestamp("issued_at"); if (ia != null) r.setIssuedAt(ia.toLocalDateTime());
            r.setRequester(rs.getString("requester"));
            r.setNotes(rs.getString("notes"));
            list.add(r);
        }
        return list;
    }
}
