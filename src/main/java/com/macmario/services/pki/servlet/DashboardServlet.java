package com.macmario.services.pki.servlet;

import com.macmario.services.pki.service.CaService;
import com.macmario.services.pki.service.CertificateService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/dashboard", ""})
public class DashboardServlet extends HttpServlet {
    private final CaService caService = new CaService();
    private final CertificateService certService = new CertificateService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            req.setAttribute("caList",       caService.findAll());
            req.setAttribute("totalCerts",   certService.countTotal());
            req.setAttribute("validCerts",   certService.countByStatus("VALID"));
            req.setAttribute("revokedCerts", certService.countByStatus("REVOKED"));
            req.setAttribute("expiringSoon", certService.findExpiringSoon(30));
            req.setAttribute("recentCerts",  certService.findExpiringSoon(9999).stream().limit(10).toList());
            req.setAttribute("page", "dashboard");
        } catch (Exception e) {
            req.setAttribute("error", e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, resp);
    }
}
