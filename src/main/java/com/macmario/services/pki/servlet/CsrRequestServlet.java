package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.CsrRequest;
import com.macmario.services.pki.service.CsrRequestService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Public CSR submission page — no auth required.
 * GET  /public/csr          → upload form
 * GET  /public/csr/{token}  → status / download signed cert
 * POST /public/csr          → submit CSR
 */
@WebServlet("/public/csr/*")
public class CsrRequestServlet extends HttpServlet {
    private final CsrRequestService csrService = new CsrRequestService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isEmpty()) {
            req.getRequestDispatcher("/WEB-INF/views/csr-request.jsp").forward(req, resp);
        } else {
            // status lookup by tracking token
            String token = path.substring(1);
            try {
                csrService.findByToken(token).ifPresentOrElse(r -> {
                    try {
                        req.setAttribute("csrRequest", r);
                        req.getRequestDispatcher("/WEB-INF/views/csr-status.jsp").forward(req, resp);
                    } catch (Exception ex) { throw new RuntimeException(ex); }
                }, () -> {
                    try { resp.sendError(404, "Request not found"); }
                    catch (IOException ex) { throw new RuntimeException(ex); }
                });
            } catch (RuntimeException | SQLException e) {
                resp.sendError(500, e.getMessage());
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String csrPem         = req.getParameter("csrPem");
        String requesterName  = req.getParameter("requesterName");
        String requesterEmail = req.getParameter("requesterEmail");
        String notes          = req.getParameter("notes");

        if (csrPem == null || !csrPem.contains("BEGIN CERTIFICATE REQUEST")) {
            req.setAttribute("error", "Please paste a valid PEM-encoded PKCS#10 CSR.");
            req.getRequestDispatcher("/WEB-INF/views/csr-request.jsp").forward(req, resp);
            return;
        }
        try {
            CsrRequest submitted = csrService.submit(csrPem, requesterName, requesterEmail, notes);
            req.setAttribute("token", submitted.getTrackingToken());
            req.setAttribute("subjectCn", submitted.getSubjectCn());
            req.getRequestDispatcher("/WEB-INF/views/csr-confirm.jsp").forward(req, resp);
        } catch (SQLException e) {
            req.setAttribute("error", "Submission failed: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/csr-request.jsp").forward(req, resp);
        }
    }
}
