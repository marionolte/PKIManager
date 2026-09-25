package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.entity.RevokedCertificate;
import com.macmario.services.pki.service.CaService;
import com.macmario.services.pki.service.CertificateService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Optional;

@WebServlet("/ca/*")
public class CaServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(CaServlet.class);
    private final CaService caService = new CaService();
    private final CertificateService certService = new CertificateService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isEmpty()) {
            try { req.setAttribute("caList", caService.findAll()); }
            catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
            req.setAttribute("page", "ca-list");
            req.getRequestDispatcher("/WEB-INF/views/ca-list.jsp").forward(req, resp);
        } else if (path.equals("/create")) {
            try { req.setAttribute("allCas", caService.findAll()); }
            catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/ca-form.jsp").forward(req, resp);
        } else if (path.equals("/import")) {
            try { req.setAttribute("allCas", caService.findAll()); }
            catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/ca-import.jsp").forward(req, resp);
        } else if (path.matches("/\\d+")) {
            try {
                Long id = Long.parseLong(path.substring(1));
                Optional<CaConfig> ca = caService.findById(id);
                if (ca.isEmpty()) { resp.sendError(404, "CA not found"); return; }
                req.setAttribute("ca",           ca.get());
                req.setAttribute("children",     caService.findChildren(id));
                req.setAttribute("certificates", certService.findByCa(id));
                req.setAttribute("certCount",    certService.countTotal());
                req.setAttribute("issuedCount",  caService.countIssuedCerts(id));
                req.setAttribute("revocationReasons", RevokedCertificate.RevocationReason.values());
            } catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/ca-detail.jsp").forward(req, resp);
        } else if (path.matches("/\\d+/cert\\.pem")) {
            try {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/cert")));
                caService.findById(id).ifPresent(ca -> {
                    try {
                        resp.setContentType("application/x-pem-file");
                        resp.setHeader("Content-Disposition",
                            "attachment; filename=\"" + ca.getRoleName() + "-cacert.pem\"");
                        resp.getWriter().write(ca.getCertificatePem() != null ? ca.getCertificatePem() : "");
                    } catch (IOException ex) { log.error("Download error", ex); }
                });
            } catch (NumberFormatException | SQLException e) { resp.sendError(500, e.getMessage()); }
        } else { resp.sendError(404); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";

        if (path.equals("/create")) {
            handleCreate(req, resp);
        } else if (path.equals("/import")) {
            handleImport(req, resp);
        } else if (path.matches("/\\d+/disable")) {
            handleStatusChange(req, resp, idFrom(path, "/disable"), "disable");
        } else if (path.matches("/\\d+/enable")) {
            handleStatusChange(req, resp, idFrom(path, "/enable"), "enable");
        } else if (path.matches("/\\d+/revoke")) {
            handleRevoke(req, resp, idFrom(path, "/revoke"));
        } else if (path.matches("/\\d+/delete")) {
            handleDelete(req, resp, idFrom(path, "/delete"));
        } else {
            resp.sendError(404);
        }
    }

    private long idFrom(String path, String suffix) {
        return Long.parseLong(path.substring(1, path.indexOf(suffix)));
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            String caType = req.getParameter("caType");
            String parentId = req.getParameter("parentCaId");
            CaConfig ca = buildFromRequest(req);
            if (!"ROOT".equals(caType) && (parentId == null || parentId.isBlank()))
                throw new IllegalArgumentException("A parent CA must be selected for INTERMEDIATE and ISSUING CAs");
            CaConfig saved;
            if ("ROOT".equals(caType))              saved = caService.createRootCa(ca);
            else if ("INTERMEDIATE".equals(caType)) saved = caService.createSubCa(ca, Long.parseLong(parentId));
            else                                    saved = caService.createIssuingCa(ca, Long.parseLong(parentId));
            resp.sendRedirect(req.getContextPath() + "/ca/" + saved.getId());
        } catch (GeneralSecurityException | OperatorCreationException | IOException | SQLException | IllegalArgumentException e) {
            log.error("CA creation failed", e);
            req.setAttribute("error", e.getMessage());
            try { req.setAttribute("allCas", caService.findAll()); } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/ca-form.jsp").forward(req, resp);
        }
    }

    private void handleImport(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            requireAdmin(req);
            CaConfig ca = buildFromRequest(req);
            String certPem = req.getParameter("certificatePem");
            String keyPem  = req.getParameter("privateKeyPem");
            String keyPw   = req.getParameter("keyPassword");
            String parentId = req.getParameter("parentCaId");
            Long pid = (parentId != null && !parentId.isBlank()) ? Long.parseLong(parentId) : null;
            CaConfig saved = caService.importCa(ca, certPem, keyPem, keyPw, pid);
            resp.sendRedirect(req.getContextPath() + "/ca/" + saved.getId());
        } catch (GeneralSecurityException | OperatorCreationException | IOException | SQLException
                 | IllegalArgumentException | SecurityException e) {
            log.error("CA import failed", e);
            req.setAttribute("error", e.getMessage());
            // Re-render the import form with non-secret fields only (never the key or password).
            req.setAttribute("importRoleName",    req.getParameter("roleName"));
            req.setAttribute("importDisplayName", req.getParameter("displayName"));
            req.setAttribute("importCaType",      req.getParameter("caType"));
            req.setAttribute("importParentCaId",  req.getParameter("parentCaId"));
            req.setAttribute("importCertificate", req.getParameter("certificatePem"));
            try { req.setAttribute("allCas", caService.findAll()); } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/ca-import.jsp").forward(req, resp);
        }
    }

    private void handleStatusChange(HttpServletRequest req, HttpServletResponse resp, long id, String action)
            throws ServletException, IOException {
        try {
            if ("disable".equals(action)) caService.disable(id); else caService.enable(id);
            resp.sendRedirect(req.getContextPath() + "/ca/" + id);
        } catch (SQLException | IllegalArgumentException e) {
            forwardToDetailWithError(req, resp, id, e);
        }
    }

    private void handleRevoke(HttpServletRequest req, HttpServletResponse resp, long id)
            throws ServletException, IOException {
        try {
            requireAdmin(req);
            String reason  = req.getParameter("reason");
            String comment = req.getParameter("comment");
            caService.revoke(id, reason, currentUsername(req), comment);
            resp.sendRedirect(req.getContextPath() + "/ca/" + id);
        } catch (SQLException | IllegalArgumentException | SecurityException e) {
            forwardToDetailWithError(req, resp, id, e);
        }
    }

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp, long id)
            throws ServletException, IOException {
        try {
            requireAdmin(req);
            caService.delete(id);
            resp.sendRedirect(req.getContextPath() + "/ca/");
        } catch (SQLException | IllegalArgumentException | SecurityException e) {
            forwardToDetailWithError(req, resp, id, e);
        }
    }

    private void forwardToDetailWithError(HttpServletRequest req, HttpServletResponse resp, long id, Exception e)
            throws ServletException, IOException {
        log.error("CA operation failed for id={}", id, e);
        req.setAttribute("error", e.getMessage());
        try {
            caService.findById(id).ifPresent(ca -> req.setAttribute("ca", ca));
            req.setAttribute("children",     caService.findChildren(id));
            req.setAttribute("certificates", certService.findByCa(id));
            req.setAttribute("certCount",    certService.countTotal());
            req.setAttribute("issuedCount",  caService.countIssuedCerts(id));
            req.setAttribute("revocationReasons", RevokedCertificate.RevocationReason.values());
        } catch (SQLException ignored) {}
        if (req.getAttribute("ca") == null) { resp.sendError(404); return; }
        req.getRequestDispatcher("/WEB-INF/views/ca-detail.jsp").forward(req, resp);
    }

    private void requireAdmin(HttpServletRequest req) {
        PkiUser user = (PkiUser) req.getSession().getAttribute("currentUser");
        if (user == null || !user.isAdmin())
            throw new SecurityException("Administrator privileges are required for this action");
    }

    private String currentUsername(HttpServletRequest req) {
        PkiUser user = (PkiUser) req.getSession().getAttribute("currentUser");
        return user != null ? user.getUsername() : "unknown";
    }

    private CaConfig buildFromRequest(HttpServletRequest req) {
        CaConfig ca = new CaConfig();
        ca.setRoleName(req.getParameter("roleName"));
        ca.setDisplayName(req.getParameter("displayName"));
        ca.setCommonName(req.getParameter("commonName"));
        ca.setCountry(nn(req.getParameter("country")));
        ca.setState(nn(req.getParameter("state")));
        ca.setLocality(nn(req.getParameter("locality")));
        ca.setOrganization(nn(req.getParameter("organization")));
        ca.setOrgUnit(nn(req.getParameter("orgUnit")));
        ca.setEmailAddress(nn(req.getParameter("emailAddress")));
        ca.setCrlUrl(nn(req.getParameter("crlUrl")));
        ca.setOcspUrl(nn(req.getParameter("ocspUrl")));
        ca.setPermittedDomains(nn(req.getParameter("permittedDomains")));
        String md = req.getParameter("defaultMd"); ca.setDefaultMd(md != null ? md : "sha256");
        String days = req.getParameter("defaultDays"); if (days != null && !days.isBlank()) ca.setDefaultDays(Integer.parseInt(days));
        String ks = req.getParameter("keySize"); if (ks != null && !ks.isBlank()) ca.setKeySize(Integer.parseInt(ks));
        return ca;
    }

    private String nn(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
