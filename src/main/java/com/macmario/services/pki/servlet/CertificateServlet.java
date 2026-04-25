package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.entity.CertificateRecord;
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

import java.io.IOException;
import java.util.Optional;

@WebServlet("/cert/*")
public class CertificateServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(CertificateServlet.class);
    private final CaService caService = new CaService();
    private final CertificateService certService = new CertificateService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isEmpty()) {
            try { req.setAttribute("certificates", certService.findAll()); }
            catch (Exception e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/cert-list.jsp").forward(req, resp);
        } else if (path.equals("/issue")) {
            try { req.setAttribute("allCas", caService.findAll()); }
            catch (Exception e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/cert-form.jsp").forward(req, resp);
        } else if (path.matches("/\\d+")) {
            try {
                Long id = Long.parseLong(path.substring(1));
                Optional<CertificateRecord> cert = certService.findById(id);
                if (cert.isEmpty()) { resp.sendError(404, "Certificate not found"); return; }
                req.setAttribute("cert", cert.get());
                req.setAttribute("revocationReasons", RevokedCertificate.RevocationReason.values());
            } catch (Exception e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/cert-detail.jsp").forward(req, resp);
        } else if (path.matches("/\\d+/download\\.pem")) {
            try {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/download")));
                certService.findById(id).ifPresent(cert -> {
                    try {
                        resp.setContentType("application/x-pem-file");
                        resp.setHeader("Content-Disposition",
                            "attachment; filename=\"" + cert.getCommonName().replace(" ","_") + ".pem\"");
                        resp.getWriter().write(cert.getCertificatePem() != null ? cert.getCertificatePem() : "");
                    } catch (IOException ex) { log.error("Download error", ex); }
                });
            } catch (Exception e) { resp.sendError(500, e.getMessage()); }
        } else { resp.sendError(404); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";
        try {
            if (path.equals("/issue")) {
                Long caId = Long.parseLong(req.getParameter("caId"));
                CaConfig ca = caService.findById(caId)
                    .orElseThrow(() -> new IllegalArgumentException("CA not found: " + caId));
                CertificateRecord template = buildTemplate(req);
                String csrPem = req.getParameter("csrPem");
                CertificateRecord saved;
                if (csrPem != null && !csrPem.isBlank() && csrPem.contains("BEGIN CERTIFICATE REQUEST")) {
                    saved = certService.signExternalCsr(csrPem.trim(), ca, template);
                } else {
                    saved = certService.generateAndIssue(ca, template);
                }
                resp.sendRedirect(req.getContextPath() + "/cert/" + saved.getId());
            } else if (path.matches("/\\d+/revoke")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/revoke")));
                String reason = req.getParameter("reason");
                String comment = req.getParameter("comment");
                certService.revoke(id, reason, "admin", comment);
                resp.sendRedirect(req.getContextPath() + "/cert/" + id);
            } else { resp.sendError(404); }
        } catch (Exception e) {
            log.error("Certificate operation failed", e);
            req.setAttribute("error", e.getMessage());
            try { req.setAttribute("allCas", caService.findAll()); } catch (Exception ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/cert-form.jsp").forward(req, resp);
        }
    }

    private CertificateRecord buildTemplate(HttpServletRequest req) {
        CertificateRecord cr = new CertificateRecord();
        cr.setCommonName(nn(req.getParameter("commonName")));
        if (cr.getCommonName() == null) cr.setCommonName("from-csr");
        cr.setCountry(nn(req.getParameter("country")));
        cr.setState(nn(req.getParameter("state")));
        cr.setLocality(nn(req.getParameter("locality")));
        cr.setOrganization(nn(req.getParameter("organization")));
        cr.setOrgUnit(nn(req.getParameter("orgUnit")));
        cr.setEmailAddress(nn(req.getParameter("emailAddress")));
        cr.setSanDns(nn(req.getParameter("sanDns")));
        cr.setSanIp(nn(req.getParameter("sanIp")));
        cr.setRequester(nn(req.getParameter("requester")));
        cr.setNotes(nn(req.getParameter("notes")));
        String ct = req.getParameter("certType");
        if (ct != null && !ct.isBlank()) cr.setCertType(CertificateRecord.CertType.valueOf(ct));
        String ks = req.getParameter("keySize");
        if (ks != null && !ks.isBlank()) cr.setKeySize(Integer.parseInt(ks));
        return cr;
    }

    private String nn(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
