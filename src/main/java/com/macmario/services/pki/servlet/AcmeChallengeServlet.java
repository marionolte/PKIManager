package com.macmario.services.pki.servlet;

import com.macmario.services.pki.service.AcmeClientService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Serves ACME HTTP-01 challenge tokens.
 * Let's Encrypt calls GET /.well-known/acme-challenge/{token}
 * We respond with the key authorisation string stored during the ACME flow.
 */
@WebServlet("/.well-known/acme-challenge/*")
public class AcmeChallengeServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.length() < 2) { resp.sendError(404); return; }
        String token = path.substring(1);
        try {
            String keyAuth = AcmeClientService.getChallengeKeyAuth(token);
            if (keyAuth == null) { resp.sendError(404); return; }
            resp.setContentType("text/plain");
            resp.getWriter().write(keyAuth);
        } catch (SQLException e) {
            resp.sendError(500, e.getMessage());
        }
    }
}
