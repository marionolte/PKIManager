package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.util.EntityManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public CaConfig createRootCa(CaConfig ca) throws Exception {
        ca.setCaType(CaConfig.CaType.ROOT);
        ca.setParentCaId(null);
        crypto.initRootCa(ca);
        return persist(ca);
    }

    public CaConfig createSubCa(CaConfig ca, Long parentCaId) throws Exception {
        CaConfig parent = findById(parentCaId)
            .orElseThrow(() -> new IllegalArgumentException("Parent CA not found: " + parentCaId));
        ca.setCaType(CaConfig.CaType.INTERMEDIATE);
        ca.setParentCaId(parent.getId());
        crypto.initSubCa(ca, parent);
        return persist(ca);
    }

    public CaConfig createIssuingCa(CaConfig ca, Long parentCaId) throws Exception {
        CaConfig parent = findById(parentCaId)
            .orElseThrow(() -> new IllegalArgumentException("Parent CA not found: " + parentCaId));
        ca.setCaType(CaConfig.CaType.ISSUING);
        ca.setParentCaId(parent.getId());
        crypto.initSubCa(ca, parent);
        return persist(ca);
    }

    public void disable(Long id) throws SQLException { updateStatus(id, CaConfig.CaStatus.DISABLED); }
    public void enable(Long id)  throws SQLException { updateStatus(id, CaConfig.CaStatus.ACTIVE); }

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

    private CaConfig persist(CaConfig ca) throws SQLException {
        String sql = """
            INSERT INTO CA_CONFIG (role_name,display_name,ca_type,status,parent_ca_id,
                country,state,locality,organization,org_unit,common_name,email_address,
                default_days,default_md,key_size,crl_url,ocsp_url,
                certificate_pem,private_key_pem,serial_number,valid_from,valid_until,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
            ps.setString(18, ca.getCertificatePem());
            ps.setString(19, ca.getPrivateKeyPem());
            ps.setString(20, ca.getSerialNumber());
            ps.setTimestamp(21, ca.getValidFrom() != null ? Timestamp.valueOf(ca.getValidFrom()) : null);
            ps.setTimestamp(22, ca.getValidUntil() != null ? Timestamp.valueOf(ca.getValidUntil()) : null);
            ps.setTimestamp(23, Timestamp.valueOf(LocalDateTime.now()));
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
