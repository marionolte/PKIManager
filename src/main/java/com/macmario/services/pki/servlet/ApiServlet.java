package com.macmario.services.pki.servlet;

import com.google.gson.*;
import com.macmario.services.pki.entity.*;
import com.macmario.services.pki.service.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.bouncycastle.operator.OperatorCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * REST API for external applications. All endpoints require X-API-Key header.
 * Each API client can only access its own certificates; CA management is prohibited.
 *
 * GET  /api/v1/cas                 — list available issuing CAs
 * GET  /api/v1/certs               — list my certificates
 * GET  /api/v1/certs/{id}          — get my certificate
 * GET  /api/v1/certs/{id}/pem      — download certificate PEM
 * POST /api/v1/certs               — issue new certificate (key pair generated server-side)
 * POST /api/v1/certs/sign          — auto-sign external CSR, returns certificate immediately
 * GET  /api/v1/csr                 — list my CSR jobs
 * GET  /api/v1/csr/{id}            — get CSR job status
 */
@WebServlet("/api/v1/*")
public class ApiServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(ApiServlet.class);
    private final CaService caService = new CaService();
    private final CertificateService certService = new CertificateService();
    private final CsrRequestService csrService = new CsrRequestService();

    // ── GET ───────────────────────────────────────────────────────────────────

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        ApiClient client = (ApiClient) req.getAttribute("apiClient");
        String path = req.getPathInfo();
        if (path == null || path.isEmpty()) path = "/";

        try {
            if (path.equals("/") || path.equals("/v1") || path.equals("/v1/")) {
                ok(resp, apiInfo());
            } else if (path.equals("/cas")) {
                listCas(resp);
            } else if (path.equals("/certs") || path.equals("/certs/")) {
                listCerts(resp, client);
            } else if (path.matches("/certs/\\d+")) {
                getCert(resp, client, Long.parseLong(path.substring(7)));
            } else if (path.matches("/certs/\\d+/pem")) {
                int end = path.lastIndexOf("/pem");
                downloadPem(req, resp, client, Long.parseLong(path.substring(7, end)));
            } else if (path.equals("/csr") || path.equals("/csr/")) {
                listCsrJobs(resp, client);
            } else if (path.matches("/csr/\\d+")) {
                getCsrJob(resp, client, Long.parseLong(path.substring(5)));
            } else {
                err(resp, 404, "Not found");
            }
        } catch (SQLException e) {
            log.error("API GET error", e);
            err(resp, 500, "Database error");
        }
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        ApiClient client = (ApiClient) req.getAttribute("apiClient");
        String path = req.getPathInfo();
        if (path == null || path.isEmpty()) path = "/";

        try {
            if (path.equals("/certs") || path.equals("/certs/")) {
                issueCert(req, resp, client);
            } else if (path.equals("/certs/sign")) {
                signCsr(req, resp, client);
            } else {
                err(resp, 404, "Not found");
            }
        } catch (IllegalArgumentException e) {
            err(resp, 400, e.getMessage());
        } catch (GeneralSecurityException | OperatorCreationException | IOException | SQLException e) {
            log.error("API POST error", e);
            err(resp, 500, e.getMessage());
        }
    }

    // ── handlers ──────────────────────────────────────────────────────────────

    private void listCas(HttpServletResponse resp) throws SQLException, IOException {
        JsonArray arr = new JsonArray();
        for (CaConfig ca : caService.findAll()) {
            if (ca.getCaType() != CaConfig.CaType.ISSUING || ca.getStatus() != CaConfig.CaStatus.ACTIVE)
                continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", ca.getId());
            o.addProperty("displayName", ca.getDisplayName());
            o.addProperty("commonName", ca.getCommonName());
            arr.add(o);
        }
        ok(resp, arr);
    }

    private void listCerts(HttpServletResponse resp, ApiClient client) throws SQLException, IOException {
        JsonArray arr = new JsonArray();
        for (CertificateRecord c : certService.findByApiClient(client.getId()))
            arr.add(certJson(c, false));
        ok(resp, arr);
    }

    private void getCert(HttpServletResponse resp, ApiClient client, Long id)
            throws SQLException, IOException {
        Optional<CertificateRecord> opt = certService.findById(id);
        if (opt.isEmpty()) { err(resp, 404, "Certificate not found"); return; }
        CertificateRecord c = opt.get();
        if (!client.getId().equals(c.getApiClientId())) { err(resp, 403, "Forbidden"); return; }
        ok(resp, certJson(c, false));
    }

    private void downloadPem(HttpServletRequest req, HttpServletResponse resp,
                              ApiClient client, Long id) throws SQLException, IOException {
        Optional<CertificateRecord> opt = certService.findById(id);
        if (opt.isEmpty()) { err(resp, 404, "Certificate not found"); return; }
        CertificateRecord c = opt.get();
        if (!client.getId().equals(c.getApiClientId())) { err(resp, 403, "Forbidden"); return; }
        resp.setContentType("application/x-pem-file");
        resp.setHeader("Content-Disposition",
            "attachment; filename=\"" + c.getCommonName().replace(" ", "_") + ".pem\"");
        resp.getWriter().write(c.getCertificatePem() != null ? c.getCertificatePem() : "");
    }

    private void listCsrJobs(HttpServletResponse resp, ApiClient client) throws SQLException, IOException {
        JsonArray arr = new JsonArray();
        for (CsrRequest j : csrService.findByApiClient(client.getId()))
            arr.add(csrJson(j));
        ok(resp, arr);
    }

    private void getCsrJob(HttpServletResponse resp, ApiClient client, Long id)
            throws SQLException, IOException {
        Optional<CsrRequest> opt = csrService.findById(id);
        if (opt.isEmpty()) { err(resp, 404, "CSR job not found"); return; }
        CsrRequest j = opt.get();
        if (!client.getId().equals(j.getApiClientId())) { err(resp, 403, "Forbidden"); return; }
        ok(resp, csrJson(j));
    }

    private void issueCert(HttpServletRequest req, HttpServletResponse resp, ApiClient client)
            throws GeneralSecurityException, OperatorCreationException, IOException, SQLException {
        JsonObject body = parseBody(req);
        Long caId = resolveCaId(body, client);
        CaConfig ca = requireIssuingCa(caId);
        CertificateRecord template = buildTemplate(body, client);
        CertificateRecord saved = certService.generateAndIssue(ca, template);
        created(resp, certJson(saved, true));
    }

    private void signCsr(HttpServletRequest req, HttpServletResponse resp, ApiClient client)
            throws GeneralSecurityException, OperatorCreationException, IOException, SQLException {
        JsonObject body = parseBody(req);
        String csrPem = bodyStr(body, "csrPem");
        if (csrPem == null || !csrPem.contains("BEGIN CERTIFICATE REQUEST"))
            throw new IllegalArgumentException("Missing or invalid csrPem");

        Long caId = resolveCaId(body, client);
        CaConfig ca = requireIssuingCa(caId);
        CertificateRecord template = buildTemplate(body, client);

        // Record CSR in job queue for audit, then auto-sign immediately
        CsrRequest job = csrService.submitForApiClient(
            csrPem, client.getName(), null, bodyStr(body, "notes"), client.getId());
        CertificateRecord saved = certService.signExternalCsr(csrPem, ca, template);
        csrService.sign(job.getId(), saved.getId(), ca.getId(), "Auto-signed via API client: " + client.getName());

        JsonObject json = certJson(saved, false);
        json.addProperty("csrJobId", job.getId());
        created(resp, json);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CaConfig requireIssuingCa(Long caId) throws SQLException {
        CaConfig ca = caService.findById(caId)
            .orElseThrow(() -> new IllegalArgumentException("CA not found: " + caId));
        if (ca.getStatus() != CaConfig.CaStatus.ACTIVE)
            throw new IllegalArgumentException("CA " + caId + " is disabled");
        return ca;
    }

    private Long resolveCaId(JsonObject body, ApiClient client) {
        if (body.has("caId") && !body.get("caId").isJsonNull())
            return body.get("caId").getAsLong();
        if (client.getDefaultCaId() != null)
            return client.getDefaultCaId();
        throw new IllegalArgumentException(
            "No caId in request body and no default CA configured for this API client");
    }

    private CertificateRecord buildTemplate(JsonObject body, ApiClient client) {
        CertificateRecord cr = new CertificateRecord();
        String cn = bodyStr(body, "commonName");
        if (cn == null || cn.isBlank())
            throw new IllegalArgumentException("commonName is required");
        cr.setCommonName(cn);
        cr.setOrganization(bodyStr(body, "organization"));
        cr.setOrgUnit(bodyStr(body, "orgUnit"));
        cr.setCountry(bodyStr(body, "country"));
        cr.setState(bodyStr(body, "state"));
        cr.setLocality(bodyStr(body, "locality"));
        cr.setEmailAddress(bodyStr(body, "emailAddress"));
        cr.setSanDns(bodyStr(body, "sanDns"));
        cr.setSanIp(bodyStr(body, "sanIp"));
        cr.setNotes(bodyStr(body, "notes"));
        cr.setRequester(client.getName());
        cr.setApiClientId(client.getId());
        String ct = bodyStr(body, "certType");
        if (ct != null) {
            try { cr.setCertType(CertificateRecord.CertType.valueOf(ct.toUpperCase())); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid certType: " + ct);
            }
        }
        if (body.has("keySize") && !body.get("keySize").isJsonNull())
            cr.setKeySize(body.get("keySize").getAsInt());
        else
            cr.setKeySize(2048);
        return cr;
    }

    private JsonObject certJson(CertificateRecord c, boolean includePrivateKey) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.getId());
        o.addProperty("commonName", c.getCommonName());
        o.addProperty("certType", c.getCertType() != null ? c.getCertType().name() : null);
        o.addProperty("status", c.getCertStatus() != null ? c.getCertStatus().name() : null);
        o.addProperty("serialNumber", c.getSerialNumber());
        o.addProperty("issuingCa", c.getIssuingCaDisplayName());
        o.addProperty("validFrom",
            c.getValidFrom() != null ? c.getValidFrom().toString().substring(0, 10) : null);
        o.addProperty("validUntil",
            c.getValidUntil() != null ? c.getValidUntil().toString().substring(0, 10) : null);
        o.addProperty("fingerprintSha256", c.getFingerprintSha256());
        o.addProperty("certificatePem", c.getCertificatePem());
        if (includePrivateKey)
            o.addProperty("privateKeyPem", c.getPrivateKeyPem());
        o.addProperty("pemEndpoint", "/api/v1/certs/" + c.getId() + "/pem");
        return o;
    }

    private JsonObject csrJson(CsrRequest j) {
        JsonObject o = new JsonObject();
        o.addProperty("id", j.getId());
        o.addProperty("subjectCn", j.getSubjectCn());
        o.addProperty("status", j.getStatus().name());
        o.addProperty("requestedAt",
            j.getRequestedAt() != null ? j.getRequestedAt().toString().substring(0, 16) : null);
        if (j.getSignedCertId() != null) {
            o.addProperty("signedCertId", j.getSignedCertId());
            o.addProperty("certEndpoint", "/api/v1/certs/" + j.getSignedCertId());
        }
        return o;
    }

    private JsonObject apiInfo() {
        JsonObject o = new JsonObject();
        o.addProperty("service", "PKI Manager API");
        o.addProperty("version", "1.0");
        JsonArray endpoints = new JsonArray();
        endpoints.add("GET  /api/v1/cas");
        endpoints.add("GET  /api/v1/certs");
        endpoints.add("POST /api/v1/certs");
        endpoints.add("POST /api/v1/certs/sign");
        endpoints.add("GET  /api/v1/certs/{id}");
        endpoints.add("GET  /api/v1/certs/{id}/pem");
        endpoints.add("GET  /api/v1/csr");
        endpoints.add("GET  /api/v1/csr/{id}");
        o.add("endpoints", endpoints);
        return o;
    }

    private JsonObject parseBody(HttpServletRequest req) throws IOException {
        try {
            return JsonParser.parseReader(req.getReader()).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON body");
        }
    }

    private String bodyStr(JsonObject body, String key) {
        if (!body.has(key) || body.get(key).isJsonNull()) return null;
        String v = body.get(key).getAsString().trim();
        return v.isEmpty() ? null : v;
    }

    private void ok(HttpServletResponse resp, JsonElement json) throws IOException {
        resp.setStatus(200);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(json.toString());
    }

    private void created(HttpServletResponse resp, JsonElement json) throws IOException {
        resp.setStatus(201);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(json.toString());
    }

    private void err(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        resp.getWriter().write(o.toString());
    }
}
