package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.*;
import com.macmario.services.pki.service.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.bouncycastle.operator.OperatorCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Admin CSR signing job queue.
 * GET  /admin/csr-jobs/         → list pending
 * GET  /admin/csr-jobs/{id}     → detail + sign/reject form
 * POST /admin/csr-jobs/{id}/sign   → sign with selected CA
 * POST /admin/csr-jobs/{id}/reject → reject with notes
 */
@WebServlet("/admin/csr-jobs/*")
public class CsrJobServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(CsrJobServlet.class);
    private final CsrRequestService csrService = new CsrRequestService();
    private final CaService caService = new CaService();
    private final CertificateService certService = new CertificateService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isEmpty()) {
            try {
                req.setAttribute("pending",  csrService.findByStatus(CsrRequest.Status.PENDING));
                req.setAttribute("signed",   csrService.findByStatus(CsrRequest.Status.SIGNED));
                req.setAttribute("rejected", csrService.findByStatus(CsrRequest.Status.REJECTED));
            } catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/csr-jobs.jsp").forward(req, resp);
        } else if (path.matches("/\\d+")) {
            Long id = Long.parseLong(path.substring(1));
            try {
                Optional<CsrRequest> csr = csrService.findById(id);
                if (csr.isEmpty()) { resp.sendError(404); return; }
                req.setAttribute("csrRequest", csr.get());
                req.setAttribute("issuingCas", caService.findAll().stream()
                        .filter(ca -> ca.getCaType() == CaConfig.CaType.ISSUING
                                   || ca.getCaType() == CaConfig.CaType.ROOT)
                        .toList());
            } catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/csr-job-detail.jsp").forward(req, resp);
        } else { resp.sendError(404); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) { resp.sendError(404); return; }

        try {
            if (path.matches("/\\d+/sign")) {
                Long id   = Long.parseLong(path.substring(1, path.indexOf("/sign")));
                Long caId = Long.parseLong(req.getParameter("caId"));
                String certType  = req.getParameter("certType");
                String adminNote = req.getParameter("adminNotes");

                CsrRequest csr = csrService.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("CSR not found"));
                CaConfig ca = caService.findById(caId)
                        .orElseThrow(() -> new IllegalArgumentException("CA not found"));

                CertificateRecord template = new CertificateRecord();
                template.setCertType(CertificateRecord.CertType.valueOf(
                        certType != null ? certType : "SERVER"));
                template.setRequester(req.getParameter("requester"));
                template.setNotes("Signed from CSR job #" + id);

                CertificateRecord signed = certService.signExternalCsr(csr.getCsrPem(), ca, template);
                csrService.sign(id, signed.getId(), caId, adminNote);
                resp.sendRedirect(req.getContextPath() + "/admin/csr-jobs/" + id);

            } else if (path.matches("/\\d+/reject")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/reject")));
                csrService.reject(id, req.getParameter("adminNotes"));
                resp.sendRedirect(req.getContextPath() + "/admin/csr-jobs/" + id);
            } else { resp.sendError(404); }
        } catch (GeneralSecurityException | OperatorCreationException | IOException | SQLException | IllegalArgumentException e) {
            log.error("CSR job operation failed", e);
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/csr-jobs.jsp").forward(req, resp);
        }
    }
}
