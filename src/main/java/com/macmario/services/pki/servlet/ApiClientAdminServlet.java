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
                createClient(req, resp, ctx, me);
            } else if (path.matches("/\\d+/approve")) {
                requireSameOrigin(req);
                Long id = Long.parseLong(path.substring(1, path.indexOf("/approve")));
                apiClientService.approve(id, me.getUsername());
                resp.sendRedirect(ctx + "/admin/api-clients/");
            } else if (path.matches("/\\d+/reject")) {
                requireSameOrigin(req);
                Long id = Long.parseLong(path.substring(1, path.indexOf("/reject")));
                apiClientService.reject(id, me.getUsername());
                resp.sendRedirect(ctx + "/admin/api-clients/");
            } else if (path.matches("/\\d+/enable")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/enable")));
                apiClientService.setActive(id, true);
                resp.sendRedirect(ctx + "/admin/api-clients/");
            } else if (path.matches("/\\d+/disable")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/disable")));
                // For a user-owned client, plain disable is reversible by the owner; revoke it instead
                // so the admin's action sticks (owner cannot re-enable a non-approved client).
                ApiClient client = apiClientService.findById(id).orElseThrow(
                    () -> new IllegalArgumentException("API client not found"));
                if (client.getOwnerUserId() != null) apiClientService.reject(id, me.getUsername());
                else apiClientService.setActive(id, false);
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
        } catch (SQLException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            log.error("API client admin error", e);
            req.setAttribute("error", friendlyError(e));
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

    private void createClient(HttpServletRequest req, HttpServletResponse resp, String ctx, PkiUser me)
            throws SQLException, IOException, ServletException {
        String name = req.getParameter("name");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name is required");
        if (name.trim().length() > 100)
            throw new IllegalArgumentException("Name must be at most 100 characters");
        String description = req.getParameter("description");
        String caIdStr = req.getParameter("defaultCaId");
        Long defaultCaId = (caIdStr != null && !caIdStr.isBlank()) ? Long.parseLong(caIdStr) : null;
        ApiClient created = apiClientService.create(name, description, defaultCaId, me.getId());
        req.getSession().setAttribute("newApiKey", created.getApiKey());
        req.getSession().setAttribute("newApiKeyClientId", created.getId());
        resp.sendRedirect(ctx + "/admin/api-clients/");
    }

    /** User-safe error text; never leaks SQL/constraint internals. */
    static String friendlyError(Exception e) {
        if (e instanceof SQLException se) {
            if ("23505".equals(se.getSQLState())) return "An API client with that name already exists";
            return "Request failed — please try again";
        }
        return e.getMessage();
    }

    /**
     * Reject cross-site state changes. The Origin (or Referer) host must EXACTLY match the
     * request's Host header (case-insensitive), and when both carry an explicit port those must
     * match too. The scheme is ignored so a TLS-terminating proxy (Origin https, backend http)
     * still works. Requires the proxy to preserve the Host header.
     */
    static void requireSameOrigin(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null && "null".equalsIgnoreCase(origin.trim())) origin = null; // opaque origin
        String source = origin != null ? origin : req.getHeader("Referer");
        String host = req.getHeader("Host");
        if (source == null || host == null || host.isBlank())
            throw new SecurityException("Missing Origin/Referer header");
        try {
            java.net.URI src = java.net.URI.create(source.trim());
            String srcHost = src.getHost();
            if (srcHost == null) throw new SecurityException("Cross-origin request rejected");

            // Split host header into host + optional port.
            String hostName = host; int hostPort = -1;
            int colon = host.lastIndexOf(':');
            if (colon > -1 && host.indexOf(']') < colon) { // ignore ':' inside [IPv6]
                hostName = host.substring(0, colon);
                try { hostPort = Integer.parseInt(host.substring(colon + 1)); } catch (NumberFormatException ignored) {}
            }
            hostName = hostName.replace("[", "").replace("]", "");
            String srcHostName = srcHost.replace("[", "").replace("]", "");
            if (!hostName.equalsIgnoreCase(srcHostName))
                throw new SecurityException("Cross-origin request rejected");
            int srcPort = src.getPort();
            if (srcPort > -1 && hostPort > -1 && srcPort != hostPort)
                throw new SecurityException("Cross-origin request rejected");
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Cross-origin request rejected");
        }
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
