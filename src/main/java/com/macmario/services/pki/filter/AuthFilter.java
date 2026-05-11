package com.macmario.services.pki.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebFilter(urlPatterns = {"/dashboard", "/ca/*", "/cert/*", "/admin/*"})
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        HttpSession session = request.getSession(false);
        boolean loggedIn = session != null && session.getAttribute("currentUser") != null;

        if (loggedIn) {
            chain.doFilter(req, res);
        } else {
            String ctx = request.getContextPath();
            String orig = request.getRequestURI() +
                    (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            response.sendRedirect(ctx + "/login?next=" + java.net.URLEncoder.encode(orig, "UTF-8"));
        }
    }
}
