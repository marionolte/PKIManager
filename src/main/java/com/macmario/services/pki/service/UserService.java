package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.util.EntityManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int ITERATIONS = 310_000;
    private static final int KEY_LEN = 256;

    // ── password hashing ──────────────────────────────────────────────────────

    public String hashPassword(String password, String saltHex) {
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public boolean verifyPassword(String password, String saltHex, String storedHash) {
        return hashPassword(password, saltHex).equals(storedHash);
    }

    // ── authentication ────────────────────────────────────────────────────────

    public Optional<PkiUser> authenticate(String username, String password) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM PKI_USER WHERE username=? AND active=TRUE")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            PkiUser u = map(rs);
            if (!verifyPassword(password, u.getSalt(), u.getPasswordHash())) return Optional.empty();
            // update last_login
            try (PreparedStatement upd = c.prepareStatement(
                "UPDATE PKI_USER SET last_login_at=? WHERE id=?")) {
                upd.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                upd.setLong(2, u.getId());
                upd.executeUpdate();
            }
            return Optional.of(u);
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<PkiUser> findAll() throws SQLException {
        List<PkiUser> list = new ArrayList<>();
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM PKI_USER ORDER BY username")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Optional<PkiUser> findById(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM PKI_USER WHERE id=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    public long countUsers() throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM PKI_USER")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public PkiUser createUser(String username, String password, String displayName,
                              String email, PkiUser.Role role) throws SQLException {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO PKI_USER(username,password_hash,salt,display_name,email,role,active) VALUES(?,?,?,?,?,?,TRUE)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, displayName);
            ps.setString(5, email);
            ps.setString(6, role.name());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                PkiUser u = new PkiUser();
                u.setId(keys.getLong(1));
                u.setUsername(username);
                u.setDisplayName(displayName);
                u.setEmail(email);
                u.setRole(role);
                return u;
            }
            throw new RuntimeException("No generated key");
        }
    }

    public void updateUser(Long id, String displayName, String email, PkiUser.Role role, boolean active) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE PKI_USER SET display_name=?,email=?,role=?,active=? WHERE id=?")) {
            ps.setString(1, displayName);
            ps.setString(2, email);
            ps.setString(3, role.name());
            ps.setBoolean(4, active);
            ps.setLong(5, id);
            ps.executeUpdate();
        }
    }

    public void changePassword(Long id, String newPassword) throws SQLException {
        String salt = generateSalt();
        String hash = hashPassword(newPassword, salt);
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE PKI_USER SET password_hash=?,salt=? WHERE id=?")) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public void deleteUser(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM PKI_USER WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private PkiUser map(ResultSet rs) throws SQLException {
        PkiUser u = new PkiUser();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setDisplayName(rs.getString("display_name"));
        u.setEmail(rs.getString("email"));
        u.setRole(PkiUser.Role.valueOf(rs.getString("role")));
        u.setActive(rs.getBoolean("active"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) u.setCreatedAt(ca.toLocalDateTime());
        Timestamp ll = rs.getTimestamp("last_login_at");
        if (ll != null) u.setLastLoginAt(ll.toLocalDateTime());
        return u;
    }
}
