package com.macmario.services.pki.filter;

import com.macmario.services.pki.service.ConfigService;
import com.macmario.services.pki.service.ScimService;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates SCIM requests with a static bearer token. The token's SHA-256 is stored in
 * PKI_CONFIGURATION (`scim.token.sha256`); if none is set, SCIM is disabled (401). Sessions and
 * API-client keys are intentionally NOT accepted — SCIM can grant ADMIN, so it needs its own key.
 */
@WebFilter("/scim/*")
public class ScimAuthFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ScimAuthFilter.class);
    private final ConfigService config = new ConfigService();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String stored;
        try { stored = config.get("scim.token.sha256", ""); }
        catch (RuntimeException e) { stored = ""; }

        String auth = request.getHeader("Authorization");
        String presented = (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7))
                ? auth.substring(7).trim() : null;

        boolean ok = stored != null && !stored.isBlank() && presented != null && !presented.isBlank()
                && MessageDigest.isEqual(
                        stored.getBytes(StandardCharsets.UTF_8),
                        ScimService.sha256Hex(presented).getBytes(StandardCharsets.UTF_8));

        if (!ok) {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/scim+json;charset=UTF-8");
            response.getWriter().write("{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"],"
                    + "\"detail\":\"Unauthorized\",\"status\":\"401\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
