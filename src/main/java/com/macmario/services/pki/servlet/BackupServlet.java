package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.service.BackupService;
import com.macmario.services.pki.service.ConfigService;
import com.macmario.services.pki.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Admin-only full-database backup &amp; restore.
 * GET  /admin/backup/            → page
 * GET  /admin/backup/export      → download a fresh JSON backup of the whole DB
 * GET  /admin/backup/download    → download a stored backup file (?name=…)
 * POST /admin/backup/create      → write a manual backup to disk
 * POST /admin/backup/settings    → update auto-backup settings
 * POST /admin/backup/import      → restore from an uploaded backup (multipart)
 */
@WebServlet("/admin/backup/*")
@MultipartConfig(maxFileSize = 100L * 1024 * 1024, maxRequestSize = 100L * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class BackupServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(BackupServlet.class);
    private final BackupService backupService = new BackupService();
    private final ConfigService configService = new ConfigService();
    private final UserService userService = new UserService();

    private boolean requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PkiUser me = (PkiUser) req.getSession().getAttribute("currentUser");
        if (me == null || !me.isAdmin()) { resp.sendError(403, "Forbidden"); return false; }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!requireAdmin(req, resp)) return;
        String path = req.getPathInfo();
        String ctx = req.getContextPath();
        try {
            if (path == null || path.equals("/") || path.isEmpty()) {
                showPage(req, resp);
            } else if (path.equals("/export")) {
                streamExport(resp);
            } else if (path.equals("/download")) {
                streamStored(req, resp);
            } else {
                resp.sendError(404);
            }
        } catch (Exception e) {
            log.error("Backup GET failed", e);
            resp.sendRedirect(ctx + "/admin/backup/?error=" + enc(e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!requireAdmin(req, resp)) return;
        String path = req.getPathInfo();
        if (path == null) path = "/";
        String ctx = req.getContextPath();
        try {
            if (path.equals("/create")) {
                Path f = backupService.writeBackupFile("manual");
                resp.sendRedirect(ctx + "/admin/backup/?msg=" + enc("Backup written: " + f.getFileName()));
            } else if (path.equals("/settings")) {
                configService.set("backup.auto.enabled", String.valueOf("true".equals(req.getParameter("enabled"))));
                configService.set("backup.auto.time", normalizeTime(req.getParameter("time")));
                configService.set("backup.auto.keep", String.valueOf(clampInt(req.getParameter("keep"), 1, 500, 20)));
                resp.sendRedirect(ctx + "/admin/backup/?msg=" + enc("Auto-backup settings saved (applies from the next day)"));
            } else if (path.equals("/import")) {
                handleImport(req, resp, ctx);
            } else {
                resp.sendError(404);
            }
        } catch (Exception e) {
            log.error("Backup POST failed", e);
            resp.sendRedirect(ctx + "/admin/backup/?error=" + enc(e.getMessage()));
        }
    }

    private void handleImport(HttpServletRequest req, HttpServletResponse resp, String ctx)
            throws Exception {
        PkiUser me = (PkiUser) req.getSession().getAttribute("currentUser");
        // Re-authenticate: this replaces the entire database, so require the admin's password.
        String password = req.getParameter("password");
        Optional<PkiUser> full = userService.findById(me.getId());
        if (password == null || full.isEmpty()
                || !userService.verifyPassword(password, full.get().getSalt(), full.get().getPasswordHash())) {
            resp.sendRedirect(ctx + "/admin/backup/?error=" + enc("Password confirmation failed"));
            return;
        }
        Part part = req.getPart("backupFile");
        if (part == null || part.getSize() == 0) {
            resp.sendRedirect(ctx + "/admin/backup/?error=" + enc("No backup file uploaded"));
            return;
        }
        byte[] data;
        try (InputStream in = part.getInputStream()) { data = in.readAllBytes(); }

        backupService.importFrom(data);
        // The restored DB may not contain this session's user — force a fresh login.
        req.getSession().invalidate();
        resp.sendRedirect(ctx + "/login?msg=" + enc("Database restored. Please log in again."));
    }

    private void showPage(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            req.setAttribute("backups", backupService.listBackups());
        } catch (IOException e) {
            req.setAttribute("error", e.getMessage());
        }
        req.setAttribute("autoEnabled", configService.getBool("backup.auto.enabled", true));
        req.setAttribute("autoTime", configService.get("backup.auto.time", "22:05"));
        req.setAttribute("autoKeep", configService.getInt("backup.auto.keep", 20));
        req.getRequestDispatcher("/WEB-INF/views/backup.jsp").forward(req, resp);
    }

    private void streamExport(HttpServletResponse resp) throws Exception {
        String name = "pki-backup-" + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
        resp.setHeader("Cache-Control", "no-store");
        try (OutputStream os = resp.getOutputStream()) {
            backupService.exportTo(os);
        }
    }

    private void streamStored(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Path file = backupService.resolveBackup(req.getParameter("name"));
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"");
        resp.setHeader("Cache-Control", "no-store");
        try (OutputStream os = resp.getOutputStream()) {
            Files.copy(file, os);
        }
    }

    /** Validate an HH:mm time-of-day string, falling back to 22:05. */
    private static String normalizeTime(String s) {
        try { return java.time.LocalTime.parse(s.trim()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")); }
        catch (Exception e) { return "22:05"; }
    }

    private static int clampInt(String s, int min, int max, int def) {
        try { int v = Integer.parseInt(s.trim()); return Math.max(min, Math.min(max, v)); }
        catch (Exception e) { return def; }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
