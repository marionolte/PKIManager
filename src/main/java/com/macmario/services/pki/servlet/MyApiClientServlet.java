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

/**
 * User self-service for API clients: any authenticated user can request an API
 * client (which stays PENDING until an admin approves it) and manage their own
 * approved clients. All actions are strictly scoped to the current user's clients.
 * GET  /api-clients/            → my clients + request form
 * POST /api-clients/request     → request a new API client (PENDING)
 * POST /api-clients/{id}/rotate → reveal/rotate the key (approved, own only)
 * POST /api-clients/{id}/enable|disable|delete → manage own client
 */
@WebServlet("/api-clients/*")
public class MyApiClientServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(MyApiClientServlet.class);
    private static final int MAX_PENDING_PER_USER = 5;

    private final ApiClientService apiClientService = new ApiClientService();
    private final CaService caService = new CaService();

    private PkiUser currentUser(HttpServletRequest req) {
        return (PkiUser) req.getSession().getAttribute("currentUser");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        PkiUser me = currentUser(req);
        if (me == null) { resp.sendError(403, "Forbidden"); return; }
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isEmpty()) {
            showList(req, resp, me);
        } else {
            resp.sendError(404);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        PkiUser me = currentUser(req);
        if (me == null) { resp.sendError(403, "Forbidden"); return; }
        String path = req.getPathInfo();
        if (path == null) path = "/";
        String ctx = req.getContextPath();
        try {
            ApiClientAdminServlet.requireSameOrigin(req);
            if (path.equals("/request")) {
                handleRequest(req, resp, ctx, me);
            } else if (path.matches("/\\d+/rotate")) {
                ApiClient c = requireOwned(req, me, "/rotate");
                if (!c.isApproved()) throw new IllegalStateException("API client is not approved yet");
                String newKey = apiClientService.rotateApiKey(c.getId());
                flashKey(req, newKey, c.getId());
                resp.sendRedirect(ctx + "/api-clients/");
            } else if (path.matches("/\\d+/enable")) {
                ApiClient c = requireOwned(req, me, "/enable");
                apiClientService.setActive(c.getId(), true);
                resp.sendRedirect(ctx + "/api-clients/");
            } else if (path.matches("/\\d+/disable")) {
                ApiClient c = requireOwned(req, me, "/disable");
                apiClientService.setActive(c.getId(), false);
                resp.sendRedirect(ctx + "/api-clients/");
            } else if (path.matches("/\\d+/delete")) {
                ApiClient c = requireOwned(req, me, "/delete");
                apiClientService.delete(c.getId());
                resp.sendRedirect(ctx + "/api-clients/");
            } else {
                resp.sendError(404);
            }
        } catch (SQLException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            log.warn("Self-service API client action failed for {}: {}", me.getUsername(), e.getMessage());
            req.setAttribute("error", ApiClientAdminServlet.friendlyError(e));
            showList(req, resp, me);
        }
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse resp, String ctx, PkiUser me)
            throws SQLException, IOException {
        String name = req.getParameter("name");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Name is required");
        if (name.trim().length() > 100)
            throw new IllegalArgumentException("Name must be at most 100 characters");
        String description = req.getParameter("description");
        String caIdStr = req.getParameter("defaultCaId");
        Long caId = (caIdStr != null && !caIdStr.isBlank()) ? Long.parseLong(caIdStr) : null;

        if (me.isAdmin()) {
            // Admins don't need approval — create directly and show the key once.
            ApiClient created = apiClientService.create(name, description, caId, me.getId());
            flashKey(req, created.getApiKey(), created.getId());
            resp.sendRedirect(ctx + "/api-clients/");
            return;
        }
        if (apiClientService.countPendingByOwner(me.getId()) >= MAX_PENDING_PER_USER)
            throw new IllegalStateException("You already have the maximum number of pending requests");
        apiClientService.request(name, description, caId, me.getId());
        req.getSession().setAttribute("flashMsg", "Request submitted — an administrator must approve it before the key can be used.");
        resp.sendRedirect(ctx + "/api-clients/");
    }

    /** Load a client by id from the path, enforcing that it belongs to the current user. */
    private ApiClient requireOwned(HttpServletRequest req, PkiUser me, String suffix) throws SQLException {
        String path = req.getPathInfo();
        long id = Long.parseLong(path.substring(1, path.indexOf(suffix)));
        Optional<ApiClient> owned = apiClientService.findOwned(id, me.getId());
        if (owned.isEmpty()) throw new SecurityException("Not your API client");
        return owned.get();
    }

    private void flashKey(HttpServletRequest req, String key, Long id) {
        req.getSession().setAttribute("newApiKey", key);
        req.getSession().setAttribute("newApiKeyClientId", id);
    }

    private void showList(HttpServletRequest req, HttpServletResponse resp, PkiUser me)
            throws ServletException, IOException {
        try {
            req.setAttribute("clients", apiClientService.findByOwner(me.getId()));
            req.setAttribute("allCas", caService.findAll());
            if (me.isAdmin()) req.setAttribute("pendingCount", apiClientService.countPending());
            HttpSession session = req.getSession(false);
            if (session != null) {
                req.setAttribute("newApiKey", session.getAttribute("newApiKey"));
                req.setAttribute("newApiKeyClientId", session.getAttribute("newApiKeyClientId"));
                req.setAttribute("flashMsg", session.getAttribute("flashMsg"));
                session.removeAttribute("newApiKey");
                session.removeAttribute("newApiKeyClientId");
                session.removeAttribute("flashMsg");
            }
        } catch (SQLException e) {
            req.setAttribute("error", e.getMessage());
        }
        req.getRequestDispatcher("/WEB-INF/views/my-api-clients.jsp").forward(req, resp);
    }
}
