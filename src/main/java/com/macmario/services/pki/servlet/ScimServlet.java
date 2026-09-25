package com.macmario.services.pki.servlet;

import com.macmario.services.pki.service.ScimService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * SCIM 2.0 endpoint. Thin: parses the request, delegates to {@link ScimService}, and writes the
 * result as application/scim+json. Overrides service() so PATCH is handled like any other method.
 */
@WebServlet("/scim/v2/*")
public class ScimServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(ScimServlet.class);
    private static final long MAX_BODY = 1024 * 1024; // 1 MB
    private final ScimService scim = new ScimService();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod().toUpperCase();
        String pathInfo = req.getPathInfo(); // e.g. /Users/5
        String ctx = req.getContextPath();

        // type + id from the path
        String type = null, id = null;
        if (pathInfo != null && pathInfo.length() > 1) {
            String[] parts = pathInfo.substring(1).split("/", 2);
            type = parts[0];
            if (parts.length > 1 && !parts[1].isBlank()) id = parts[1];
        }
        if (type == null) { write(resp, 404, err("No resource type in path")); return; }

        Map<String, String> query = new HashMap<>();
        if (req.getParameter("filter") != null) query.put("filter", req.getParameter("filter"));
        if (req.getParameter("startIndex") != null) query.put("startIndex", req.getParameter("startIndex"));
        if (req.getParameter("count") != null) query.put("count", req.getParameter("count"));

        JsonObject body = null;
        if ("PUT".equals(method) || "PATCH".equals(method) || "POST".equals(method)) {
            try {
                body = readBody(req);
            } catch (IllegalArgumentException e) {
                write(resp, 400, err(e.getMessage())); return;
            }
        }

        String scheme = req.getScheme();
        int port = req.getServerPort();
        String portPart = (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) ? "" : (":" + port);
        String baseUrl = scheme + "://" + req.getServerName() + portPart + ctx + "/scim/v2";

        ScimService.Result result;
        try {
            result = scim.dispatch(method, type, id, query, body, baseUrl);
        } catch (RuntimeException e) {
            log.error("SCIM dispatch error", e);
            write(resp, 500, err("Internal error"));
            return;
        }
        write(resp, result.status(), result.body());
    }

    private JsonObject readBody(HttpServletRequest req) throws IOException {
        long len = req.getContentLengthLong();
        if (len > MAX_BODY) throw new IllegalArgumentException("Request body too large");
        byte[] data = req.getInputStream().readNBytes((int) MAX_BODY + 1);
        if (data.length > MAX_BODY) throw new IllegalArgumentException("Request body too large");
        if (data.length == 0) return null;
        try {
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed JSON body");
        }
    }

    private void write(HttpServletResponse resp, int status, JsonObject body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/scim+json;charset=UTF-8");
        resp.getWriter().write(body.toString());
    }

    private JsonObject err(String detail) {
        JsonObject o = new JsonObject();
        com.google.gson.JsonArray schemas = new com.google.gson.JsonArray();
        schemas.add("urn:ietf:params:scim:api:messages:2.0:Error");
        o.add("schemas", schemas);
        o.addProperty("detail", detail);
        o.addProperty("status", "400");
        return o;
    }
}
