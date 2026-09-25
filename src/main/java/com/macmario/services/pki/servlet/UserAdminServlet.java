package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.service.ConfigService;
import com.macmario.services.pki.service.ScimService;
import com.macmario.services.pki.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Admin user management.
 * GET  /admin/users/          → list
 * GET  /admin/users/new       → create form
 * GET  /admin/users/{id}/edit → edit form
 * POST /admin/users/          → create user
 * POST /admin/users/{id}      → update user
 * POST /admin/users/{id}/delete → delete user
 * POST /admin/users/{id}/password → change password
 */
@WebServlet("/admin/users/*")
public class UserAdminServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(UserAdminServlet.class);
    private final UserService userService = new UserService();
    private final ConfigService configService = new ConfigService();

    /** User management is ADMIN-only; VIEWERs (and unauthenticated requests) get 403. */
    private boolean requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PkiUser me = (PkiUser) req.getSession().getAttribute("currentUser");
        if (me == null || !me.isAdmin()) {
            resp.sendError(403, "Forbidden");
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!requireAdmin(req, resp)) return;
        String path = req.getPathInfo();
        if (path == null || path.equals("/") || path.isEmpty()) {
            try { req.setAttribute("users", userService.findAll()); }
            catch (SQLException e) { req.setAttribute("error", e.getMessage()); }
            req.setAttribute("scimConfigured", !configService.get("scim.token.sha256", "").isBlank());
            HttpSession session = req.getSession(false);
            if (session != null && session.getAttribute("newScimToken") != null) {
                req.setAttribute("newScimToken", session.getAttribute("newScimToken"));
                session.removeAttribute("newScimToken");
            }
            req.getRequestDispatcher("/WEB-INF/views/user-list.jsp").forward(req, resp);
        } else if (path.equals("/new")) {
            req.getRequestDispatcher("/WEB-INF/views/user-form.jsp").forward(req, resp);
        } else if (path.matches("/\\d+/edit")) {
            Long id = Long.parseLong(path.substring(1, path.indexOf("/edit")));
            try {
                userService.findById(id).ifPresentOrElse(
                    u -> { req.setAttribute("user", u);
                           try { req.getRequestDispatcher("/WEB-INF/views/user-form.jsp").forward(req, resp); }
                           catch (Exception ex) { throw new RuntimeException(ex); } },
                    () -> { try { resp.sendError(404); } catch (IOException ex) { throw new RuntimeException(ex); } }
                );
            } catch (SQLException | RuntimeException e) { resp.sendError(500, e.getMessage()); }
        } else { resp.sendError(404); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!requireAdmin(req, resp)) return;
        String path = req.getPathInfo();
        if (path == null) path = "/";
        try {
            PkiUser current = (PkiUser) req.getSession().getAttribute("currentUser");
            if (path.equals("/")) {
                PkiUser.Role role = PkiUser.Role.valueOf(req.getParameter("role"));
                userService.createUser(
                    req.getParameter("username"),
                    req.getParameter("password"),
                    req.getParameter("displayName"),
                    req.getParameter("email"),
                    role
                );
                resp.sendRedirect(req.getContextPath() + "/admin/users/");
            } else if (path.equals("/scim-token")) {
                ApiClientAdminServlet.requireSameOrigin(req);
                byte[] b = new byte[32];
                new SecureRandom().nextBytes(b);
                String token = "scim_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
                configService.set("scim.token.sha256", ScimService.sha256Hex(token));
                req.getSession().setAttribute("newScimToken", token);
                resp.sendRedirect(req.getContextPath() + "/admin/users/");
            } else if (path.matches("/\\d+")) {
                Long id = Long.parseLong(path.substring(1));
                userService.updateUser(id,
                    req.getParameter("displayName"),
                    req.getParameter("email"),
                    PkiUser.Role.valueOf(req.getParameter("role")),
                    "true".equals(req.getParameter("active"))
                );
                resp.sendRedirect(req.getContextPath() + "/admin/users/");
            } else if (path.matches("/\\d+/delete")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/delete")));
                if (current != null && current.getId().equals(id))
                    throw new IllegalStateException("Cannot delete your own account");
                userService.deleteUser(id);
                resp.sendRedirect(req.getContextPath() + "/admin/users/");
            } else if (path.matches("/\\d+/password")) {
                Long id = Long.parseLong(path.substring(1, path.indexOf("/password")));
                String pw = req.getParameter("newPassword");
                if (pw == null || pw.length() < 8)
                    throw new IllegalArgumentException("Password must be at least 8 characters");
                userService.changePassword(id, pw);
                resp.sendRedirect(req.getContextPath() + "/admin/users/");
            } else { resp.sendError(404); }
        } catch (SQLException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            log.error("User operation failed", e);
            req.setAttribute("error", e.getMessage());
            try { req.setAttribute("users", userService.findAll()); } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/user-list.jsp").forward(req, resp);
        }
    }
}
