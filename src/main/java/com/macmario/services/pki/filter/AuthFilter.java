package com.macmario.services.pki.filter;

import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

@WebFilter(urlPatterns = {"/dashboard", "/ca/*", "/cert/*", "/admin/*", "/api-clients/*", "/docs/*"})
public class AuthFilter implements Filter {

    private final UserService userService = new UserService();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        HttpSession session = request.getSession(false);
        PkiUser cached = session != null ? (PkiUser) session.getAttribute("currentUser") : null;

        if (cached != null) {
            // Re-load the user each request so SCIM/admin deprovisioning and role changes
            // take effect immediately instead of after the session times out.
            try {
                Optional<PkiUser> fresh = userService.findById(cached.getId());
                if (fresh.isEmpty() || !fresh.get().isActive()) {
                    session.invalidate();
                    redirectToLogin(request, response);
                    return;
                }
                session.setAttribute("currentUser", fresh.get());
            } catch (SQLException e) {
                // Transient DB error: proceed with the cached principal rather than lock everyone out.
            }
            chain.doFilter(req, res);
        } else {
            redirectToLogin(request, response);
        }
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String ctx = request.getContextPath();
        String orig = request.getRequestURI() +
                (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        response.sendRedirect(ctx + "/login?next=" + java.net.URLEncoder.encode(orig, "UTF-8"));
    }
}
