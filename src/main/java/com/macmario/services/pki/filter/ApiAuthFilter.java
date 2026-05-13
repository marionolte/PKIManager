package com.macmario.services.pki.filter;

import com.macmario.services.pki.entity.ApiClient;
import com.macmario.services.pki.service.ApiClientService;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

@WebFilter("/api/*")
public class ApiAuthFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ApiAuthFilter.class);
    private final ApiClientService service = new ApiClientService();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            deny(response, 401, "Missing X-API-Key header");
            return;
        }
        try {
            Optional<ApiClient> client = service.findByApiKey(apiKey);
            if (client.isEmpty() || !client.get().isActive()) {
                deny(response, 401, "Invalid or inactive API key");
                return;
            }
            request.setAttribute("apiClient", client.get());
            chain.doFilter(req, res);
        } catch (SQLException e) {
            log.error("API auth DB error", e);
            deny(response, 500, "Internal server error");
        }
    }

    private void deny(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
