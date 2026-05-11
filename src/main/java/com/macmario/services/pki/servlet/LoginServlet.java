package com.macmario.services.pki.servlet;

import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession s = req.getSession(false);
        if (s != null && s.getAttribute("currentUser") != null) {
            resp.sendRedirect(req.getContextPath() + "/dashboard");
            return;
        }
        req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String next     = req.getParameter("next");

        try {
            Optional<PkiUser> user = userService.authenticate(username, password);
            if (user.isPresent()) {
                HttpSession session = req.getSession(true);
                session.setAttribute("currentUser", user.get());
                session.setMaxInactiveInterval(3600);
                String dest = (next != null && !next.isBlank() && next.startsWith("/")) ? next
                              : req.getContextPath() + "/dashboard";
                resp.sendRedirect(dest);
            } else {
                req.setAttribute("error", "Invalid username or password.");
                req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
            }
        } catch (SQLException e) {
            req.setAttribute("error", "Login error: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
        }
    }
}
