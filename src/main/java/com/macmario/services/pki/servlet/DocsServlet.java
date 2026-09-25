package com.macmario.services.pki.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves the in-app online documentation. Topics are looked up against a fixed allowlist
 * (topic → fragment JSP), so the include path can never come from user input.
 * GET /docs/           → overview
 * GET /docs/{topic}    → certificates | scim | api-clients | acme
 */
@WebServlet("/docs/*")
public class DocsServlet extends HttpServlet {

    /** topic key → [display title, fragment path under /WEB-INF/views/docs]. */
    private static final Map<String, String[]> TOPICS = new LinkedHashMap<>();
    static {
        TOPICS.put("overview",     new String[]{"Documentation",        "/WEB-INF/views/docs/overview.jsp"});
        TOPICS.put("certificates", new String[]{"Certificates",         "/WEB-INF/views/docs/certificates.jsp"});
        TOPICS.put("scim",         new String[]{"SCIM 2.0",             "/WEB-INF/views/docs/scim.jsp"});
        TOPICS.put("api-clients",  new String[]{"API Clients",          "/WEB-INF/views/docs/api-clients.jsp"});
        TOPICS.put("acme",         new String[]{"ACME / Let's Encrypt", "/WEB-INF/views/docs/acme.jsp"});
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        String topic = (path == null || path.equals("/") || path.isEmpty())
                ? "overview" : path.substring(1).toLowerCase();

        String[] entry = TOPICS.get(topic);
        if (entry == null) { resp.sendError(404, "Unknown documentation topic"); return; }

        req.setAttribute("topic", topic);
        req.setAttribute("docTitle", entry[0]);
        req.setAttribute("contentPage", entry[1]); // fixed, allowlisted path — safe to include
        req.getRequestDispatcher("/WEB-INF/views/docs.jsp").forward(req, resp);
    }
}
