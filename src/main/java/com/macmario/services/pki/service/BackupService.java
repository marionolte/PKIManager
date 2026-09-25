package com.macmario.services.pki.service;

import com.macmario.services.pki.util.EntityManagerProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Full database export / import ("backup & restore") plus change-triggered
 * automatic backups. Everything is serialized as JSON (never executable SQL),
 * and restore runs in a single transaction so a failure leaves the DB untouched.
 */
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    public static final String FORMAT = "pki-manager-backup";
    public static final int VERSION = 1;

    /** Shared with the auto-backup scheduler so a tick never runs mid-restore. */
    private static final ReentrantLock BACKUP_LOCK = new ReentrantLock();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String FP_FILE = ".last-fingerprint";

    /** Tables in dependency order (parents before children) — used for insert. */
    private static final List<String> TABLES = List.of(
        "CA_CONFIG", "CERTIFICATE_RECORD", "REVOKED_CERTIFICATE",
        "API_CLIENT", "CSR_REQUEST", "ACME_CERTIFICATE",
        "ACME_CHALLENGE_TOKEN", "PKI_USER", "PKI_CONFIGURATION"
    );
    private static final Set<String> KNOWN = new HashSet<>(TABLES);

    /** Delete order (children before parents); CA_CONFIG handled specially last. */
    private static final List<String> DELETE_ORDER = List.of(
        "REVOKED_CERTIFICATE", "CERTIFICATE_RECORD", "API_CLIENT", "CSR_REQUEST",
        "ACME_CHALLENGE_TOKEN", "ACME_CERTIFICATE", "PKI_USER", "PKI_CONFIGURATION"
    );

    /** Identity (AUTO_INCREMENT id) tables — sequences must be realigned after import. */
    private static final List<String> IDENTITY_TABLES = List.of(
        "CA_CONFIG", "CERTIFICATE_RECORD", "REVOKED_CERTIFICATE",
        "API_CLIENT", "CSR_REQUEST", "ACME_CERTIFICATE", "PKI_USER"
    );

    private static final Map<String, String> ORDER_BY = Map.of(
        "CA_CONFIG", "id", "CERTIFICATE_RECORD", "id", "REVOKED_CERTIFICATE", "id",
        "API_CLIENT", "id", "CSR_REQUEST", "id", "ACME_CERTIFICATE", "id",
        "ACME_CHALLENGE_TOKEN", "token", "PKI_USER", "id", "PKI_CONFIGURATION", "cfg_key"
    );

    /** Columns that change on ordinary reads/logins — excluded from the change fingerprint. */
    private static final Map<String, Set<String>> VOLATILE = Map.of(
        "API_CLIENT", Set.of("last_used_at", "last_ip", "last_user_agent"),
        "PKI_USER", Set.of("last_login_at")
    );
    /** Tables ignored entirely by the change fingerprint (ephemeral data). */
    private static final Set<String> FP_SKIP_TABLES = Set.of("ACME_CHALLENGE_TOKEN");

    // ── export ──────────────────────────────────────────────────────────────

    /** Stream a full JSON backup of every table to {@code out}. */
    public void exportTo(OutputStream out) throws SQLException, IOException {
        try (Connection c = EntityManagerProvider.getConnection()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try (JsonWriter w = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                w.setIndent("  ");
                w.beginObject();
                w.name("format").value(FORMAT);
                w.name("version").value(VERSION);
                w.name("exportedAt").value(LocalDateTime.now().toString());
                w.name("tables").beginObject();
                for (String table : TABLES) {
                    w.name(table).beginArray();
                    writeRows(c, table, w, null);
                    w.endArray();
                }
                w.endObject();
                w.endObject();
                w.flush();
            } finally {
                c.rollback(); // read-only snapshot
            }
        }
    }

    private void writeRows(Connection c, String table, JsonWriter w, Set<String> exclude)
            throws SQLException, IOException {
        String sql = "SELECT * FROM " + table + " ORDER BY " + ORDER_BY.get(table);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            while (rs.next()) {
                w.beginObject();
                for (int i = 1; i <= n; i++) {
                    String col = md.getColumnName(i).toLowerCase();
                    if (exclude != null && exclude.contains(col)) continue;
                    w.name(col);
                    writeValue(w, rs, i, md.getColumnType(i));
                }
                w.endObject();
            }
        }
    }

    private void writeValue(JsonWriter w, ResultSet rs, int idx, int sqlType) throws SQLException, IOException {
        if (rs.getObject(idx) == null) { w.nullValue(); return; }
        switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> w.value(rs.getBoolean(idx));
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> w.value(rs.getLong(idx));
            case Types.TIMESTAMP -> w.value(rs.getTimestamp(idx).toLocalDateTime().toString());
            case Types.DATE -> w.value(rs.getDate(idx).toLocalDate().toString());
            default -> w.value(rs.getString(idx));
        }
    }

    // ── change fingerprint ──────────────────────────────────────────────────

    /** SHA-256 over all meaningful data (volatile columns and ephemeral tables excluded). */
    public String computeFingerprint() throws SQLException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (Connection c = EntityManagerProvider.getConnection()) {
                for (String table : TABLES) {
                    if (FP_SKIP_TABLES.contains(table)) continue;
                    Set<String> exclude = VOLATILE.get(table);
                    String sql = "SELECT * FROM " + table + " ORDER BY " + ORDER_BY.get(table);
                    try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int n = meta.getColumnCount();
                        while (rs.next()) {
                            md.update(table.getBytes(StandardCharsets.UTF_8));
                            for (int i = 1; i <= n; i++) {
                                String col = meta.getColumnName(i).toLowerCase();
                                if (exclude != null && exclude.contains(col)) continue;
                                Object v = rs.getObject(i);
                                md.update(col.getBytes(StandardCharsets.UTF_8));
                                md.update((byte) 0);
                                md.update((v == null ? "\u0000null" : rs.getString(i)).getBytes(StandardCharsets.UTF_8));
                                md.update((byte) 0);
                            }
                        }
                    }
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── import (restore) ────────────────────────────────────────────────────

    /**
     * Restore the database from a JSON backup, atomically. Validates the file,
     * writes a pre-import safety backup, then replaces all data in one transaction.
     * The caller MUST have verified the current admin's identity first.
     */
    public String importFrom(byte[] jsonBytes) throws SQLException, IOException {
        JsonObject root = JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!FORMAT.equals(getStr(root, "format")))
            throw new IllegalArgumentException("Not a PKI Manager backup file");
        if (!root.has("tables") || !root.get("tables").isJsonObject())
            throw new IllegalArgumentException("Backup file has no 'tables' section");
        JsonObject tables = root.getAsJsonObject("tables");
        for (String key : tables.keySet())
            if (!KNOWN.contains(key))
                throw new IllegalArgumentException("Unknown table in backup: " + key);
        requireAdminPresent(tables);

        BACKUP_LOCK.lock();
        try {
            writeBackupFile("pre-import"); // safety copy of current state

            try (Connection c = EntityManagerProvider.getConnection()) {
                c.setAutoCommit(false);
                try {
                    deleteAll(c);
                    // CA_CONFIG first without parent links, then wire parents up.
                    insertTable(c, "CA_CONFIG", arr(tables, "CA_CONFIG"), Set.of("parent_ca_id"));
                    updateCaParents(c, arr(tables, "CA_CONFIG"));
                    for (String table : TABLES) {
                        if (table.equals("CA_CONFIG")) continue;
                        insertTable(c, table, arr(tables, table), Set.of());
                    }
                    c.commit();
                } catch (SQLException | RuntimeException e) {
                    c.rollback();
                    throw new IllegalArgumentException("Restore failed and was rolled back: " + e.getMessage(), e);
                } finally {
                    c.setAutoCommit(true);
                }
                realignIdentities(c);
            }
            invalidateFingerprint(); // force the next auto-backup to capture the restored state
            log.info("Database restored from backup");
            return "Database restored successfully.";
        } finally {
            BACKUP_LOCK.unlock();
        }
    }

    private void requireAdminPresent(JsonObject tables) {
        JsonArray users = arr(tables, "PKI_USER");
        for (JsonElement el : users) {
            JsonObject u = el.getAsJsonObject();
            boolean isAdmin = u.has("role") && !u.get("role").isJsonNull()
                    && "ADMIN".equalsIgnoreCase(u.get("role").getAsString());
            boolean active = u.has("active") && !u.get("active").isJsonNull() && u.get("active").getAsBoolean();
            if (isAdmin && active) return;
        }
        throw new IllegalArgumentException("Backup contains no active ADMIN user — refusing (would lock you out)");
    }

    private void deleteAll(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String t : DELETE_ORDER) st.executeUpdate("DELETE FROM " + t);
            st.executeUpdate("UPDATE CA_CONFIG SET parent_ca_id = NULL");
            st.executeUpdate("DELETE FROM CA_CONFIG");
        }
    }

    private void insertTable(Connection c, String table, JsonArray rows, Set<String> exclude) throws SQLException {
        if (rows == null || rows.isEmpty()) return;
        Map<String, Integer> colTypes = currentColumns(c, table);
        // Columns present in the backup AND in the current schema, minus excluded ones.
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        for (JsonElement el : rows)
            for (String k : el.getAsJsonObject().keySet())
                if (colTypes.containsKey(k) && !exclude.contains(k)) cols.add(k);
        if (cols.isEmpty()) return;

        String colList = String.join(",", cols);
        String qs = String.join(",", Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + colList + ") VALUES (" + qs + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (JsonElement el : rows) {
                JsonObject row = el.getAsJsonObject();
                int i = 1;
                for (String col : cols)
                    bind(ps, i++, colTypes.get(col), row.has(col) ? row.get(col) : null);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void updateCaParents(Connection c, JsonArray rows) throws SQLException {
        if (rows == null) return;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE CA_CONFIG SET parent_ca_id=? WHERE id=?")) {
            boolean any = false;
            for (JsonElement el : rows) {
                JsonObject row = el.getAsJsonObject();
                if (!row.has("parent_ca_id") || row.get("parent_ca_id").isJsonNull()) continue;
                ps.setLong(1, row.get("parent_ca_id").getAsLong());
                ps.setLong(2, row.get("id").getAsLong());
                ps.addBatch();
                any = true;
            }
            if (any) ps.executeBatch();
        }
    }

    private void realignIdentities(Connection c) {
        for (String table : IDENTITY_TABLES) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM " + table)) {
                long next = rs.next() ? rs.getLong(1) : 1;
                st.executeUpdate("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + next);
            } catch (SQLException e) {
                log.warn("Could not realign identity for {}: {}", table, e.getMessage());
            }
        }
    }

    private Map<String, Integer> currentColumns(Connection c, String table) throws SQLException {
        Map<String, Integer> cols = new LinkedHashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++)
                cols.put(md.getColumnName(i).toLowerCase(), md.getColumnType(i));
        }
        return cols;
    }

    private void bind(PreparedStatement ps, int idx, int sqlType, JsonElement el) throws SQLException {
        if (el == null || el.isJsonNull()) { ps.setNull(idx, sqlType); return; }
        switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> ps.setBoolean(idx, el.getAsBoolean());
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> ps.setInt(idx, el.getAsInt());
            case Types.BIGINT -> ps.setLong(idx, el.getAsLong());
            case Types.TIMESTAMP -> ps.setTimestamp(idx, Timestamp.valueOf(LocalDateTime.parse(el.getAsString())));
            case Types.DATE -> ps.setDate(idx, java.sql.Date.valueOf(el.getAsString()));
            default -> ps.setString(idx, el.getAsString());
        }
    }

    // ── backup files on disk + retention ──────────────────────────────────────

    public Path backupDir() throws IOException {
        Path dir = EntityManagerProvider.getDataDir().resolve("backups");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        return dir;
    }

    /** Write a backup file with the given prefix (auto | manual | pre-import). */
    public Path writeBackupFile(String prefix) throws SQLException, IOException {
        Path dir = backupDir();
        Path file = dir.resolve(prefix + "-" + LocalDateTime.now().format(TS) + ".json");
        try (OutputStream os = Files.newOutputStream(file)) {
            exportTo(os);
        }
        restrictPermissions(file);
        return file;
    }

    private void restrictPermissions(Path file) {
        try {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (IOException | UnsupportedOperationException e) {
            log.debug("Could not restrict backup file permissions: {}", e.getMessage());
        }
    }

    public List<Path> listBackups() throws IOException {
        Path dir = backupDir();
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        }
    }

    private static final java.util.regex.Pattern SAFE_NAME =
        java.util.regex.Pattern.compile("^(auto|manual|pre-import)-[\\w-]+\\.json$");

    /** Resolve a backup file name safely (allowlist + path-traversal guard). */
    public Path resolveBackup(String name) throws IOException {
        if (name == null || !SAFE_NAME.matcher(name).matches())
            throw new IllegalArgumentException("Invalid backup name");
        Path dir = backupDir().toRealPath();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.exists(file))
            throw new IllegalArgumentException("Backup not found");
        return file;
    }

    public void enforceRetention(int keep) throws IOException {
        Path dir = backupDir();
        try (var stream = Files.list(dir)) {
            List<Path> autos = stream
                .filter(p -> p.getFileName().toString().startsWith("auto-"))
                .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                .toList();
            for (int i = keep; i < autos.size(); i++) {
                try { Files.deleteIfExists(autos.get(i)); }
                catch (IOException e) { log.warn("Could not delete old backup {}: {}", autos.get(i), e.getMessage()); }
            }
        }
    }

    // ── change-triggered auto backup ──────────────────────────────────────────

    /** One scheduler tick: write a new auto backup only when the data changed. */
    public void autoBackupTick(int keep) {
        BACKUP_LOCK.lock();
        try {
            String fp = computeFingerprint();
            String last = readFingerprint();
            if (fp.equals(last)) return;
            Path file = writeBackupFile("auto");
            writeFingerprint(fp);
            enforceRetention(keep);
            log.info("Auto backup written (data changed): {}", file.getFileName());
        } catch (SQLException | IOException e) {
            log.warn("Auto backup tick failed: {}", e.getMessage());
        } finally {
            BACKUP_LOCK.unlock();
        }
    }

    private String readFingerprint() {
        try {
            Path fp = backupDir().resolve(FP_FILE);
            return Files.exists(fp) ? Files.readString(fp).trim() : null;
        } catch (IOException e) { return null; }
    }

    private void writeFingerprint(String fp) {
        try { Files.writeString(backupDir().resolve(FP_FILE), fp); }
        catch (IOException e) { log.warn("Could not persist fingerprint: {}", e.getMessage()); }
    }

    private void invalidateFingerprint() {
        try { Files.deleteIfExists(backupDir().resolve(FP_FILE)); }
        catch (IOException ignored) {}
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static String getStr(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static JsonArray arr(JsonObject tables, String name) {
        return tables.has(name) && tables.get(name).isJsonArray() ? tables.getAsJsonArray(name) : new JsonArray();
    }
}
