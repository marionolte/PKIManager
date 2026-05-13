package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.ApiClient;
import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.service.ApiClientService;
import com.macmario.services.pki.service.CaService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

@WebServlet("/admin/api-clients/*")
public class ApiClientAdminServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(ApiClientAdminServlet.class);
    private final ApiClientService apiClientService = new ApiClientService();
    private final CaService caService = new CaService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        PkiUser me = (PkiUser) req.getSession().getAttribute("currentUser");
        if (me == null || !me.isAdmin()) {
            resp.sendError(403, "Forbidden");
            return;
        }
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isEmpty()) {
            showList(req, resp);
        } else if (path.matches("/\\d+/edit")) {
            showEdit(req, resp, Long.parseLong(path.substring(1, path.indexOf("/edit"))));
        } else {
            resp.sendError(404);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        PkiUser me = (PkiUser) req.getSession().getAttribute("currentUser");
        if (me == null || !me.isAdmin()) {
            resp.sendError(403, "Forbidden");
            return;
        }
        String path = req.getPathInfo();
        if (path == null) path = "/";
        String ctx = req.getContextPath();

        try {
            if (path.equals("/create")) {
                createClient(req, resp, ctx);
            } else if (path.matches("/\\d+/enable")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/enable")));
                apiClientService.setActive(id, true);
                resp.sendRedirect(ctx + "/admin/api-clients/");
            } else if (path.matches("/\\d+/disable")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/disable")));
                apiClientService.setActive(id, false);
                resp.sendRedirect(ctx + "/admin/api-clients/");
            } else if (path.matches("/\\d+/rotate")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/rotate")));
                String newKey = apiClientService.rotateApiKey(id);
                req.getSession().setAttribute("newApiKey", newKey);
                req.getSession().setAttribute("newApiKeyClientId", id);
                resp.sendRedirect(ctx + "/admin/api-clients/");
            } else if (path.matches("/\\d+/delete")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/delete")));
                apiClientService.delete(id);
                resp.sendRedirect(ctx + "/admin/api-clients/");
            } else if (path.matches("/\\d+")) {
                Long id = Long.parseLong(path.substring(1));
                updateClient(req, resp, ctx, id);
            } else {
                resp.sendError(404);
            }
        } catch (SQLException | IllegalArgumentException e) {
            log.error("API client admin error", e);
            req.setAttribute("error", e.getMessage());
            showList(req, resp);
        }
    }

    private void showList(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            req.setAttribute("clients", apiClientService.findAll());
            req.setAttribute("allCas", caService.findAll());
            // Consume flash attributes
            HttpSession session = req.getSession(false);
            if (session != null) {
                req.setAttribute("newApiKey", session.getAttribute("newApiKey"));
                req.setAttribute("newApiKeyClientId", session.getAttribute("newApiKeyClientId"));
                session.removeAttribute("newApiKey");
                session.removeAttribute("newApiKeyClientId");
            }
        } catch (SQLException e) {
            req.setAttribute("error", e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/api-clients.jsp").forward(req, resp);
    }

    private void showEdit(HttpServletRequest req, HttpServletResponse resp, Long id)
            throws ServletException, IOException {
        try {
            Optional<ApiClient> client = apiClientService.findById(id);
            if (client.isEmpty()) { resp.sendError(404); return; }
            req.setAttribute("client", client.get());
            req.setAttribute("allCas", caService.findAll());
        } catch (SQLException e) {
            req.setAttribute("error", e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/api-clients.jsp").forward(req, resp);
    }

    private void createClient(HttpServletRequest req, HttpServletResponse resp, String ctx)
            throws SQLException, IOException, ServletException {
        String name = req.getParameter("name");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name is required");
        String description = req.getParameter("description");
        String caIdStr = req.getParameter("defaultCaId");
        Long defaultCaId = (caIdStr != null && !caIdStr.isBlank()) ? Long.parseLong(caIdStr) : null;
        ApiClient created = apiClientService.create(name, description, defaultCaId);
        req.getSession().setAttribute("newApiKey", created.getApiKey());
        req.getSession().setAttribute("newApiKeyClientId", created.getId());
        resp.sendRedirect(ctx + "/admin/api-clients/");
    }

    private void updateClient(HttpServletRequest req, HttpServletResponse resp, String ctx, Long id)
            throws SQLException, IOException, ServletException {
        String name = req.getParameter("name");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name is required");
        String description = req.getParameter("description");
        String caIdStr = req.getParameter("defaultCaId");
        Long defaultCaId = (caIdStr != null && !caIdStr.isBlank()) ? Long.parseLong(caIdStr) : null;
        apiClientService.update(id, name, description, defaultCaId);
        resp.sendRedirect(ctx + "/admin/api-clients/");
    }
}
