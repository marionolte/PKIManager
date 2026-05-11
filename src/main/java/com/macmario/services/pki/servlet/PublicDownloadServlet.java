package com.macmario.services.pki.servlet;

import com.macmario.services.pki.util.EntityManagerProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.*;

/**
 * Serves certificate PEM by download token — no auth required.
 * URL: /public/download/{token}
 */
@WebServlet("/public/download/*")
public class PublicDownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.length() < 2) { resp.sendError(400, "Missing token"); return; }
        String token = path.substring(1);

        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT common_name, certificate_pem FROM CERTIFICATE_RECORD WHERE download_token=?")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { resp.sendError(404, "Certificate not found or link invalid"); return; }
            String cn  = rs.getString("common_name");
            String pem = rs.getString("certificate_pem");
            if (pem == null || pem.isBlank()) { resp.sendError(404, "Certificate data unavailable"); return; }

            String filename = cn.replaceAll("[^a-zA-Z0-9._-]", "_") + ".pem";
            resp.setContentType("application/x-pem-file");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            resp.getWriter().write(pem);
        } catch (SQLException e) {
            resp.sendError(500, e.getMessage());
        }
    }
}
