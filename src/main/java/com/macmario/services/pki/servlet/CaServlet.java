package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.CaConfig;
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
            catch (Exception e) { req.setAttribute("error", e.getMessage()); }
            req.setAttribute("page", "ca-list");
            req.getRequestDispatcher("/WEB-INF/views/ca-list.jsp").forward(req, resp);
        } else if (path.equals("/create")) {
            try { req.setAttribute("allCas", caService.findAll()); }
            catch (Exception e) { req.setAttribute("error", e.getMessage()); }
            req.getRequestDispatcher("/WEB-INF/views/ca-form.jsp").forward(req, resp);
        } else if (path.matches("/\\d+")) {
            try {
                Long id = Long.parseLong(path.substring(1));
                Optional<CaConfig> ca = caService.findById(id);
                if (ca.isEmpty()) { resp.sendError(404, "CA not found"); return; }
                req.setAttribute("ca",           ca.get());
                req.setAttribute("children",     caService.findChildren(id));
                req.setAttribute("certificates", certService.findByCa(id));
                req.setAttribute("certCount",    certService.countTotal());
            } catch (Exception e) { req.setAttribute("error", e.getMessage()); }
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
            } catch (Exception e) { resp.sendError(500, e.getMessage()); }
        } else { resp.sendError(404); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) path = "/";
        try {
            if (path.equals("/create")) {
                String caType = req.getParameter("caType");
                String parentId = req.getParameter("parentCaId");
                CaConfig ca = buildFromRequest(req);
                CaConfig saved;
                if ("ROOT".equals(caType))          saved = caService.createRootCa(ca);
                else if ("INTERMEDIATE".equals(caType)) saved = caService.createSubCa(ca, Long.parseLong(parentId));
                else                                saved = caService.createIssuingCa(ca, Long.parseLong(parentId));
                resp.sendRedirect(req.getContextPath() + "/ca/" + saved.getId());
            } else if (path.matches("/\\d+/disable")) {
                caService.disable(Long.parseLong(path.substring(1, path.indexOf("/disable"))));
                resp.sendRedirect(req.getContextPath() + "/ca/" + path.substring(1, path.indexOf("/disable")));
            } else if (path.matches("/\\d+/enable")) {
                caService.enable(Long.parseLong(path.substring(1, path.indexOf("/enable"))));
                resp.sendRedirect(req.getContextPath() + "/ca/" + path.substring(1, path.indexOf("/enable")));
            } else { resp.sendError(404); }
        } catch (Exception e) {
            log.error("CA operation failed", e);
            req.setAttribute("error", e.getMessage());
            try { req.setAttribute("allCas", caService.findAll()); } catch (Exception ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/ca-form.jsp").forward(req, resp);
        }
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
        String md = req.getParameter("defaultMd"); ca.setDefaultMd(md != null ? md : "sha256");
        String days = req.getParameter("defaultDays"); if (days != null && !days.isBlank()) ca.setDefaultDays(Integer.parseInt(days));
        String ks = req.getParameter("keySize"); if (ks != null && !ks.isBlank()) ca.setKeySize(Integer.parseInt(ks));
        return ca;
    }

    private String nn(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
