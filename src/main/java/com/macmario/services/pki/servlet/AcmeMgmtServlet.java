package com.macmario.services.pki.servlet;

import com.macmario.services.pki.service.AcmeClientService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Admin ACME / Let's Encrypt management.
 * GET  /admin/acme            → list managed domains
 * POST /admin/acme/register   → register a new domain
 * POST /admin/acme/{id}/request → trigger certificate request (async)
 * POST /admin/acme/{id}/delete  → remove entry
 */
@WebServlet("/admin/acme/*")
public class AcmeMgmtServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(AcmeMgmtServlet.class);
    private final AcmeClientService acmeService = new AcmeClientService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try { req.setAttribute("acmeList", acmeService.findAll()); }
        catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
        req.getRequestDispatcher("/WEB-INF/views/acme-mgmt.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";
        try {
            if (path.equals("/register")) {
                String domain = req.getParameter("domain");
                String email  = req.getParameter("contactEmail");
                if (domain == null || domain.isBlank()) throw new IllegalArgumentException("Domain is required");
                acmeService.registerDomain(domain.trim(), email, "true".equals(req.getParameter("staging")));
                resp.sendRedirect(req.getContextPath() + "/admin/acme");
            } else if (path.matches("/\\d+/request")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/request")));
                String email   = req.getParameter("contactEmail");
                boolean staging = "true".equals(req.getParameter("staging"));
                // Run async — ACME flow can take 30-60s
                executor.submit(() -> {
                    try { acmeService.requestCertificate(id, email, staging); }
                    catch (Exception e) { log.error("ACME request failed for domain id {}", id, e); }
                });
                req.setAttribute("info", "Certificate request started. Refresh in ~60 seconds to see the result.");
                req.setAttribute("acmeList", acmeService.findAll());
                req.getRequestDispatcher("/WEB-INF/views/acme-mgmt.jsp").forward(req, resp);
            } else if (path.matches("/\\d+/delete")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/delete")));
                acmeService.delete(id);
                resp.sendRedirect(req.getContextPath() + "/admin/acme");
            } else { resp.sendError(404); }
        } catch (GeneralSecurityException | IOException | SQLException | IllegalArgumentException e) {
            log.error("ACME operation failed", e);
            req.setAttribute("error", e.getMessage());
            try { req.setAttribute("acmeList", acmeService.findAll()); } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/acme-mgmt.jsp").forward(req, resp);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
