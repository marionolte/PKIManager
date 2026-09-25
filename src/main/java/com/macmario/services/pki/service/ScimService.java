package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.ApiClient;
import com.macmario.services.pki.entity.CertificateRecord;
import com.macmario.services.pki.entity.PkiUser;
import com.macmario.services.pki.entity.RevokedCertificate;
import com.macmario.services.pki.util.EntityManagerProvider;
import com.google.gson.*;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCIM 2.0 (RFC 7643/7644) core logic — pure and servlet-independent so it can be unit-tested.
 * Supports list, get, PUT and PATCH for Users, Groups (roles), Certificates and ApiClients, plus
 * the discovery endpoints. All JSON is built by hand so secrets (password hashes, private keys,
 * API keys, download tokens) can never leak.
 */
public class ScimService {

    public static final String CORE_USER   = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String CORE_GROUP  = "urn:ietf:params:scim:schemas:core:2.0:Group";
    public static final String CERT_URN    = "urn:macmario:params:scim:schemas:pki:2.0:Certificate";
    public static final String APICLI_URN  = "urn:macmario:params:scim:schemas:pki:2.0:ApiClient";
    public static final String LIST_URN    = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    public static final String PATCH_URN   = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
    public static final String ERROR_URN   = "urn:ietf:params:scim:api:messages:2.0:Error";

    private static final int MAX_COUNT = 200;
    private static final Pattern FILTER = Pattern.compile("(\\w+)\\s+eq\\s+\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private final UserService userService = new UserService();
    private final CertificateService certService = new CertificateService();
    private final ApiClientService apiClientService = new ApiClientService();

    public record Result(int status, JsonObject body) {}

    /** SHA-256 hex of a string (used to store/compare the SCIM bearer token). */
    public static String sha256Hex(String s) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Thrown internally to short-circuit to a SCIM error response. */
    private static class ScimError extends RuntimeException {
        final int status; final String scimType;
        ScimError(int status, String scimType, String detail) { super(detail); this.status = status; this.scimType = scimType; }
    }

    /**
     * @param method  GET/PUT/PATCH/POST/DELETE
     * @param type    Users|Groups|Certificates|ApiClients|ServiceProviderConfig|ResourceTypes|Schemas
     * @param id      resource id, or null for a collection
     * @param query   request parameters (filter, startIndex, count)
     * @param body    parsed JSON request body (may be null)
     * @param baseUrl absolute base, e.g. https://host/pki-manager/scim/v2
     */
    public Result dispatch(String method, String type, String id, Map<String, String> query,
                           JsonObject body, String baseUrl) {
        try {
            return switch (type) {
                case "ServiceProviderConfig" -> new Result(200, serviceProviderConfig(baseUrl));
                case "ResourceTypes" -> new Result(200, resourceTypes(baseUrl));
                case "Schemas" -> new Result(200, schemas());
                case "Users" -> users(method, id, query, body, baseUrl);
                case "Groups" -> groups(method, id, query, body, baseUrl);
                case "Certificates" -> certificates(method, id, query, body, baseUrl);
                case "ApiClients" -> apiClients(method, id, query, body, baseUrl);
                default -> new Result(404, error(404, null, "Unknown resource type: " + type));
            };
        } catch (ScimError e) {
            return new Result(e.status, error(e.status, e.scimType, e.getMessage()));
        } catch (SQLException e) {
            return new Result(500, error(500, null, "Internal error"));
        } catch (NumberFormatException e) {
            return new Result(404, error(404, null, "Resource not found"));
        }
    }

    // ── Users ────────────────────────────────────────────────────────────────

    private Result users(String method, String id, Map<String,String> query, JsonObject body, String baseUrl)
            throws SQLException {
        if ("GET".equals(method) && id == null) {
            List<PkiUser> all = userService.findAll();
            String[] f = parseFilter(query.get("filter"), Set.of("username", "id"));
            if (f != null) {
                String attr = f[0], val = f[1];
                all = all.stream().filter(u -> "username".equals(attr)
                        ? u.getUsername().equalsIgnoreCase(val) : String.valueOf(u.getId()).equals(val)).toList();
            }
            all = all.stream().sorted(Comparator.comparing(PkiUser::getId)).toList();
            return page(all, query, baseUrl, u -> userJson(u, baseUrl), "User");
        }
        PkiUser u = (id == null) ? null : userService.findById(Long.parseLong(id)).orElse(null);
        if (u == null) return new Result(404, error(404, null, "User not found"));
        return switch (method) {
            case "GET"   -> new Result(200, userJson(u, baseUrl));
            case "PUT"   -> new Result(200, putUser(u, body, baseUrl));
            case "PATCH" -> new Result(200, patchUser(u, body, baseUrl));
            case "DELETE"-> throw new ScimError(501, null, "DELETE not implemented");
            default      -> throw new ScimError(405, null, "Method not allowed");
        };
    }

    private JsonObject putUser(PkiUser u, JsonObject body, String baseUrl) throws SQLException {
        req(body);
        String userName = optString(body, "userName");
        if (userName != null && !userName.equals(u.getUsername()))
            throw new ScimError(400, "mutability", "userName is immutable");
        String display = firstNonNull(nested(body, "name", "formatted"), optString(body, "displayName"), u.getDisplayName());
        String email = emailFrom(body, u.getEmail());
        boolean active = body.has("active") ? asBool(body.get("active")) : u.isActive();
        guardLastAdmin(u, active, u.getRole());
        userService.updateUser(u.getId(), display, email, u.getRole(), active);
        return userJson(userService.findById(u.getId()).orElseThrow(), baseUrl);
    }

    private JsonObject patchUser(PkiUser u, JsonObject body, String baseUrl) throws SQLException {
        String display = u.getDisplayName(); String email = u.getEmail(); boolean active = u.isActive(); String password = null;
        for (JsonObject op : patchOps(body)) {
            String opName = op.get("op").getAsString().toLowerCase();
            String path = optString(op, "path");
            JsonElement value = op.get("value");
            if ("remove".equals(opName) && path != null) {
                switch (norm(path)) { case "active" -> active = false; default -> throw new ScimError(400, "invalidPath", "Cannot remove " + path); }
                continue;
            }
            if (path == null) { // no-path: value is an object of attributes
                if (value == null || !value.isJsonObject()) throw new ScimError(400, "invalidValue", "Missing value object");
                JsonObject o = value.getAsJsonObject();
                if (o.has("userName") && !o.get("userName").getAsString().equals(u.getUsername()))
                    throw new ScimError(400, "mutability", "userName is immutable");
                if (o.has("active")) active = asBool(o.get("active"));
                if (o.has("displayName")) display = o.get("displayName").getAsString();
                if (o.has("name")) display = firstNonNull(nested(o, "name", "formatted"), display);
                if (o.has("emails")) email = emailFrom(o, email);
                if (o.has("password")) password = o.get("password").getAsString();
            } else {
                switch (norm(path)) {
                    case "active" -> active = asBool(value);
                    case "displayname", "name.formatted" -> display = value.getAsString();
                    case "emails" -> email = emailValue(value);
                    case "password" -> password = value.getAsString();
                    case "username" -> throw new ScimError(400, "mutability", "userName is immutable");
                    default -> throw new ScimError(400, "invalidPath", "Unsupported path: " + path);
                }
            }
        }
        if (password != null) {
            if (password.length() < 8) throw new ScimError(400, "invalidValue", "Password must be at least 8 characters");
            userService.changePassword(u.getId(), password);
        }
        guardLastAdmin(u, active, u.getRole());
        userService.updateUser(u.getId(), display, email, u.getRole(), active);
        return userJson(userService.findById(u.getId()).orElseThrow(), baseUrl);
    }

    private JsonObject userJson(PkiUser u, String baseUrl) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr(CORE_USER));
        o.addProperty("id", String.valueOf(u.getId()));
        o.addProperty("userName", u.getUsername());
        o.addProperty("displayName", u.getDisplayName());
        JsonObject name = new JsonObject(); name.addProperty("formatted", u.getDisplayName() != null ? u.getDisplayName() : "");
        o.add("name", name);
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            JsonObject em = new JsonObject(); em.addProperty("value", u.getEmail()); em.addProperty("primary", true);
            JsonArray emails = new JsonArray(); emails.add(em); o.add("emails", emails);
        }
        o.addProperty("active", u.isActive());
        JsonObject grp = new JsonObject(); grp.addProperty("value", u.getRole().name()); grp.addProperty("display", u.getRole().name());
        JsonArray groups = new JsonArray(); groups.add(grp); o.add("groups", groups);
        o.add("meta", meta("User", String.valueOf(u.getId()), baseUrl + "/Users/" + u.getId()));
        return o;
    }

    private void guardLastAdmin(PkiUser u, boolean newActive, PkiUser.Role newRole) throws SQLException {
        boolean wasCounting = u.getRole() == PkiUser.Role.ADMIN && u.isActive();
        boolean stillCounting = newRole == PkiUser.Role.ADMIN && newActive;
        if (wasCounting && !stillCounting && userService.countActiveAdmins() <= 1)
            throw new ScimError(400, "invalidValue", "Cannot remove the last active administrator");
    }

    // ── Groups (roles) ─────────────────────────────────────────────────────────

    private Result groups(String method, String id, Map<String,String> query, JsonObject body, String baseUrl)
            throws SQLException {
        List<String> roles = List.of("ADMIN", "VIEWER");
        if ("GET".equals(method) && id == null) {
            List<String> list = new ArrayList<>(roles);
            String[] f = parseFilter(query.get("filter"), Set.of("displayname", "id"));
            if (f != null) list = list.stream().filter(r -> r.equalsIgnoreCase(f[1])).toList();
            List<PkiUser> allUsers = userService.findAll();
            return page(list, query, baseUrl, r -> groupJson(r, baseUrl, allUsers), "Group");
        }
        if (id == null || !roles.contains(id.toUpperCase())) return new Result(404, error(404, null, "Group not found"));
        String role = id.toUpperCase();
        return switch (method) {
            case "GET"   -> new Result(200, groupJson(role, baseUrl, userService.findAll()));
            case "PUT"   -> new Result(200, putGroup(role, body, baseUrl));
            case "PATCH" -> new Result(200, patchGroup(role, body, baseUrl));
            default      -> throw new ScimError(405, null, "Method not allowed");
        };
    }

    private JsonObject putGroup(String role, JsonObject body, String baseUrl) throws SQLException {
        req(body);
        if (!"ADMIN".equals(role)) throw new ScimError(400, "mutability", "Only the ADMIN group membership can be set authoritatively");
        List<Long> ids = memberIds(body.get("members"));
        applyAdminSet(new HashSet<>(ids));
        return groupJson(role, baseUrl, userService.findAll());
    }

    private JsonObject patchGroup(String role, JsonObject body, String baseUrl) throws SQLException {
        for (JsonObject op : patchOps(body)) {
            String opName = op.get("op").getAsString().toLowerCase();
            String path = optString(op, "path");
            List<Long> ids = memberIdsFromOp(op, path);
            boolean admin = "ADMIN".equals(role);
            switch (opName) {
                case "add" -> { for (Long uid : ids) { if (admin) promote(uid); else demote(List.of(uid)); } }
                case "remove" -> { if (admin) demote(ids); else throw new ScimError(400, "invalidValue", "Cannot remove members from VIEWER (it is the complement of ADMIN)"); }
                case "replace" -> { if (admin) applyAdminSet(new HashSet<>(ids)); else demote(ids); }
                default -> throw new ScimError(400, "invalidSyntax", "Unknown op: " + opName);
            }
        }
        return groupJson(role, baseUrl, userService.findAll());
    }

    private void promote(Long uid) throws SQLException {
        userService.findById(uid).orElseThrow(() -> new ScimError(404, null, "User not found: " + uid));
        userService.setRole(uid, PkiUser.Role.ADMIN);
    }

    /** Demote users to VIEWER, refusing if it would remove the last active admin. */
    private void demote(List<Long> ids) throws SQLException {
        long losing = 0;
        for (Long uid : ids) {
            PkiUser u = userService.findById(uid).orElseThrow(() -> new ScimError(404, null, "User not found: " + uid));
            if (u.getRole() == PkiUser.Role.ADMIN && u.isActive()) losing++;
        }
        if (losing > 0 && userService.countActiveAdmins() - losing < 1)
            throw new ScimError(400, "invalidValue", "Cannot remove the last active administrator");
        for (Long uid : ids) userService.setRole(uid, PkiUser.Role.VIEWER);
    }

    /** Make exactly {@code adminIds} the admins; everyone else becomes VIEWER. */
    private void applyAdminSet(Set<Long> adminIds) throws SQLException {
        List<PkiUser> all = userService.findAll();
        long resultingActiveAdmins = all.stream()
            .filter(u -> adminIds.contains(u.getId()) && u.isActive()).count();
        if (resultingActiveAdmins < 1)
            throw new ScimError(400, "invalidValue", "Resulting membership would leave no active administrator");
        for (PkiUser u : all) {
            PkiUser.Role target = adminIds.contains(u.getId()) ? PkiUser.Role.ADMIN : PkiUser.Role.VIEWER;
            if (u.getRole() != target) userService.setRole(u.getId(), target);
        }
    }

    private JsonObject groupJson(String role, String baseUrl, List<PkiUser> allUsers) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr(CORE_GROUP));
        o.addProperty("id", role);
        o.addProperty("displayName", role);
        JsonArray members = new JsonArray();
        for (PkiUser u : allUsers) {
            if (u.getRole().name().equals(role)) {
                JsonObject m = new JsonObject();
                m.addProperty("value", String.valueOf(u.getId()));
                m.addProperty("display", u.getUsername());
                m.addProperty("$ref", baseUrl + "/Users/" + u.getId());
                members.add(m);
            }
        }
        o.add("members", members);
        o.add("meta", meta("Group", role, baseUrl + "/Groups/" + role));
        return o;
    }

    private List<Long> memberIdsFromOp(JsonObject op, String path) {
        if (path != null && path.startsWith("members[")) {
            Matcher m = Pattern.compile("value\\s+eq\\s+\"([^\"]*)\"").matcher(path);
            if (m.find()) return List.of(Long.parseLong(m.group(1)));
            throw new ScimError(400, "invalidPath", "Unsupported members filter");
        }
        if (path != null && !"members".equalsIgnoreCase(path))
            throw new ScimError(400, "invalidPath", "Unsupported path: " + path);
        return memberIds(op.get("value"));
    }

    private List<Long> memberIds(JsonElement value) {
        List<Long> ids = new ArrayList<>();
        if (value == null || value.isJsonNull()) return ids;
        if (value.isJsonArray()) {
            for (JsonElement el : value.getAsJsonArray()) {
                if (el.isJsonObject()) ids.add(Long.parseLong(el.getAsJsonObject().get("value").getAsString()));
                else ids.add(Long.parseLong(el.getAsString()));
            }
        } else if (value.isJsonObject()) {
            ids.add(Long.parseLong(value.getAsJsonObject().get("value").getAsString()));
        } else {
            ids.add(Long.parseLong(value.getAsString()));
        }
        return ids;
    }

    // ── Certificates (read-only except revoke) ──────────────────────────────────

    private Result certificates(String method, String id, Map<String,String> query, JsonObject body, String baseUrl)
            throws SQLException {
        if ("GET".equals(method) && id == null) {
            int start = intParam(query, "startIndex", 1);
            int count = Math.min(intParam(query, "count", 100), MAX_COUNT);
            if (query.get("filter") != null) parseFilter(query.get("filter"), Set.of("id")); // only id supported
            long total = certService.countTotal();
            JsonArray resources = new JsonArray();
            for (JsonObject c : certSummaries(start, count, baseUrl)) resources.add(c);
            return new Result(200, listResponse(resources, total, start, count));
        }
        Optional<CertificateRecord> cr = (id == null) ? Optional.empty() : certService.findById(Long.parseLong(id));
        if (cr.isEmpty()) return new Result(404, error(404, null, "Certificate not found"));
        return switch (method) {
            case "GET"   -> new Result(200, certJson(cr.get(), baseUrl, true));
            case "PATCH" -> new Result(200, patchCert(cr.get(), body, baseUrl));
            default      -> throw new ScimError(405, null, "Certificates are read-only except revocation");
        };
    }

    private JsonObject patchCert(CertificateRecord cr, JsonObject body, String baseUrl) throws SQLException {
        String newStatus = null; String reason = "UNSPECIFIED";
        for (JsonObject op : patchOps(body)) {
            String path = optString(op, "path");
            JsonElement value = op.get("value");
            if (path == null && value != null && value.isJsonObject()) {
                JsonObject o = value.getAsJsonObject();
                if (o.has("status")) newStatus = o.get("status").getAsString();
                if (o.has("revocationReason")) reason = o.get("revocationReason").getAsString();
            } else if ("status".equalsIgnoreCase(norm(path))) {
                newStatus = value.getAsString();
            } else if ("revocationreason".equals(norm(path))) {
                reason = value.getAsString();
            } else {
                throw new ScimError(400, "invalidPath", "Only 'status' is mutable on a certificate");
            }
        }
        if (newStatus == null) throw new ScimError(400, "invalidValue", "No status supplied");
        if (!"REVOKED".equalsIgnoreCase(newStatus))
            throw new ScimError(400, "invalidValue", "A certificate can only be transitioned to REVOKED");
        if (cr.getCertStatus() == CertificateRecord.CertStatus.REVOKED)
            throw new ScimError(400, "invalidValue", "Certificate is already revoked");
        validateReason(reason);
        certService.revoke(cr.getId(), reason.toUpperCase(), "scim", "Revoked via SCIM");
        return certJson(certService.findById(cr.getId()).orElseThrow(), baseUrl, true);
    }

    private void validateReason(String reason) {
        try { RevokedCertificate.RevocationReason.valueOf(reason.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new ScimError(400, "invalidValue", "Invalid revocation reason: " + reason); }
    }

    private List<JsonObject> certSummaries(int start, int count, String baseUrl) throws SQLException {
        List<JsonObject> out = new ArrayList<>();
        String sql = "SELECT id, serial_number, common_name, cert_status, cert_type, valid_from, valid_until, " +
                     "issuing_ca_id, fingerprint_sha256, san_dns, san_ip FROM CERTIFICATE_RECORD ORDER BY id " +
                     "LIMIT ? OFFSET ?";
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, count);
            ps.setInt(2, Math.max(0, start - 1));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.add("schemas", arr(CERT_URN));
                o.addProperty("id", String.valueOf(rs.getLong("id")));
                o.addProperty("serialNumber", rs.getString("serial_number"));
                o.addProperty("commonName", rs.getString("common_name"));
                o.addProperty("status", rs.getString("cert_status"));
                o.addProperty("type", rs.getString("cert_type"));
                addTs(o, "validFrom", rs.getTimestamp("valid_from"));
                addTs(o, "validUntil", rs.getTimestamp("valid_until"));
                long caId = rs.getLong("issuing_ca_id"); if (!rs.wasNull()) o.addProperty("issuingCaId", caId);
                o.addProperty("fingerprintSha256", rs.getString("fingerprint_sha256"));
                if (rs.getString("san_dns") != null) o.addProperty("sanDns", rs.getString("san_dns"));
                if (rs.getString("san_ip") != null) o.addProperty("sanIp", rs.getString("san_ip"));
                o.add("meta", meta("Certificate", o.get("id").getAsString(), baseUrl + "/Certificates/" + rs.getLong("id")));
                out.add(o);
            }
        }
        return out;
    }

    private JsonObject certJson(CertificateRecord cr, String baseUrl, boolean includePem) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr(CERT_URN));
        o.addProperty("id", String.valueOf(cr.getId()));
        o.addProperty("serialNumber", cr.getSerialNumber());
        o.addProperty("commonName", cr.getCommonName());
        o.addProperty("status", cr.getCertStatus().name());
        o.addProperty("type", cr.getCertType().name());
        if (cr.getValidFrom() != null) o.addProperty("validFrom", cr.getValidFrom().toString());
        if (cr.getValidUntil() != null) o.addProperty("validUntil", cr.getValidUntil().toString());
        if (cr.getIssuingCaId() != null) o.addProperty("issuingCaId", cr.getIssuingCaId());
        o.addProperty("fingerprintSha256", cr.getFingerprintSha256());
        if (cr.getSanDns() != null) o.addProperty("sanDns", cr.getSanDns());
        if (cr.getSanIp() != null) o.addProperty("sanIp", cr.getSanIp());
        if (includePem && cr.getCertificatePem() != null) o.addProperty("certificatePem", cr.getCertificatePem());
        o.add("meta", meta("Certificate", String.valueOf(cr.getId()), baseUrl + "/Certificates/" + cr.getId()));
        return o;
    }

    // ── ApiClients ──────────────────────────────────────────────────────────────

    private Result apiClients(String method, String id, Map<String,String> query, JsonObject body, String baseUrl)
            throws SQLException {
        if ("GET".equals(method) && id == null) {
            List<ApiClient> all = apiClientService.findAll();
            String[] f = parseFilter(query.get("filter"), Set.of("displayname", "id"));
            if (f != null) all = all.stream().filter(a -> "id".equals(f[0])
                    ? String.valueOf(a.getId()).equals(f[1]) : f[1].equalsIgnoreCase(a.getName())).toList();
            all = all.stream().sorted(Comparator.comparing(ApiClient::getId)).toList();
            return page(all, query, baseUrl, a -> apiClientJson(a, baseUrl), "ApiClient");
        }
        ApiClient ac = (id == null) ? null : apiClientService.findById(Long.parseLong(id)).orElse(null);
        if (ac == null) return new Result(404, error(404, null, "ApiClient not found"));
        return switch (method) {
            case "GET"   -> new Result(200, apiClientJson(ac, baseUrl));
            case "PUT"   -> new Result(200, mutateApiClient(ac, applyToObject(body), baseUrl));
            case "PATCH" -> new Result(200, mutateApiClient(ac, collectPatch(body), baseUrl));
            default      -> throw new ScimError(405, null, "Method not allowed");
        };
    }

    /** Collect the intended {active, approvalStatus} changes from a PATCH body. */
    private JsonObject collectPatch(JsonObject body) {
        JsonObject changes = new JsonObject();
        for (JsonObject op : patchOps(body)) {
            String path = optString(op, "path");
            JsonElement value = op.get("value");
            if (path == null && value != null && value.isJsonObject()) {
                JsonObject o = value.getAsJsonObject();
                if (o.has("active")) changes.addProperty("active", asBool(o.get("active")));
                if (o.has("approvalStatus")) changes.addProperty("approvalStatus", o.get("approvalStatus").getAsString());
            } else if ("active".equalsIgnoreCase(norm(path))) {
                changes.addProperty("active", asBool(value));
            } else if ("approvalstatus".equals(norm(path))) {
                changes.addProperty("approvalStatus", value.getAsString());
            } else {
                throw new ScimError(400, "invalidPath", "Unsupported path: " + path);
            }
        }
        return changes;
    }

    private JsonObject applyToObject(JsonObject body) {
        req(body);
        JsonObject changes = new JsonObject();
        if (body.has("active")) changes.addProperty("active", asBool(body.get("active")));
        if (body.has("approvalStatus")) changes.addProperty("approvalStatus", body.get("approvalStatus").getAsString());
        return changes;
    }

    private JsonObject mutateApiClient(ApiClient ac, JsonObject changes, String baseUrl) throws SQLException {
        if (changes.has("approvalStatus")) {
            String s = changes.get("approvalStatus").getAsString().toUpperCase();
            if ("APPROVED".equals(s)) apiClientService.approve(ac.getId(), "scim");
            else if ("REJECTED".equals(s)) apiClientService.reject(ac.getId(), "scim");
            else throw new ScimError(400, "invalidValue", "approvalStatus must be APPROVED or REJECTED");
        }
        if (changes.has("active")) {
            boolean active = changes.get("active").getAsBoolean();
            if (active) {
                try { apiClientService.setActive(ac.getId(), true); }
                catch (IllegalStateException e) { throw new ScimError(400, "invalidValue", e.getMessage()); }
            } else if (ac.getOwnerUserId() != null) {
                apiClientService.reject(ac.getId(), "scim"); // sticky revoke for owned clients
            } else {
                apiClientService.setActive(ac.getId(), false);
            }
        }
        return apiClientJson(apiClientService.findById(ac.getId()).orElseThrow(), baseUrl);
    }

    private JsonObject apiClientJson(ApiClient a, String baseUrl) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr(APICLI_URN));
        o.addProperty("id", String.valueOf(a.getId()));
        o.addProperty("displayName", a.getName());
        if (a.getDescription() != null) o.addProperty("description", a.getDescription());
        o.addProperty("active", a.isActive());
        o.addProperty("approvalStatus", a.getApprovalStatus().name());
        if (a.getOwnerUserId() != null) o.addProperty("ownerUserId", a.getOwnerUserId());
        if (a.getOwnerName() != null) o.addProperty("ownerName", a.getOwnerName());
        if (a.getDefaultCaId() != null) o.addProperty("defaultCaId", a.getDefaultCaId());
        // Note: apiKey is intentionally never emitted.
        o.add("meta", meta("ApiClient", String.valueOf(a.getId()), baseUrl + "/ApiClients/" + a.getId()));
        return o;
    }

    // ── discovery ────────────────────────────────────────────────────────────────

    private JsonObject serviceProviderConfig(String baseUrl) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
        o.add("patch", feature(true));
        o.add("bulk", featureMax(false));
        o.add("filter", filterFeature());
        o.add("changePassword", feature(true));
        o.add("sort", feature(false));
        o.add("etag", feature(false));
        JsonArray auth = new JsonArray();
        JsonObject bearer = new JsonObject();
        bearer.addProperty("type", "oauthbearertoken");
        bearer.addProperty("name", "Bearer Token");
        bearer.addProperty("description", "Static bearer token in the Authorization header");
        bearer.addProperty("primary", true);
        auth.add(bearer);
        o.add("authenticationSchemes", auth);
        o.add("meta", meta("ServiceProviderConfig", null, baseUrl + "/ServiceProviderConfig"));
        return o;
    }

    private JsonObject resourceTypes(String baseUrl) {
        JsonArray res = new JsonArray();
        res.add(resourceType("User", "/Users", CORE_USER, baseUrl));
        res.add(resourceType("Group", "/Groups", CORE_GROUP, baseUrl));
        res.add(resourceType("Certificate", "/Certificates", CERT_URN, baseUrl));
        res.add(resourceType("ApiClient", "/ApiClients", APICLI_URN, baseUrl));
        return listResponse(res, res.size(), 1, res.size());
    }

    private JsonObject resourceType(String name, String endpoint, String schema, String baseUrl) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr("urn:ietf:params:scim:schemas:core:2.0:ResourceType"));
        o.addProperty("id", name);
        o.addProperty("name", name);
        o.addProperty("endpoint", endpoint);
        o.addProperty("schema", schema);
        o.add("meta", meta("ResourceType", name, baseUrl + "/ResourceTypes/" + name));
        return o;
    }

    private JsonObject schemas() {
        JsonArray res = new JsonArray();
        for (String s : List.of(CORE_USER, CORE_GROUP, CERT_URN, APICLI_URN)) {
            JsonObject o = new JsonObject(); o.addProperty("id", s); o.addProperty("name", s.substring(s.lastIndexOf(':') + 1));
            res.add(o);
        }
        return listResponse(res, res.size(), 1, res.size());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private <T> Result page(List<T> all, Map<String,String> query, String baseUrl,
                            java.util.function.Function<T, JsonObject> mapper, String rt) {
        int start = intParam(query, "startIndex", 1);
        int count = Math.min(intParam(query, "count", 100), MAX_COUNT);
        int from = Math.max(0, start - 1);
        int to = Math.min(all.size(), from + count);
        JsonArray resources = new JsonArray();
        for (int i = from; i < to; i++) resources.add(mapper.apply(all.get(i)));
        return new Result(200, listResponse(resources, all.size(), start, count));
    }

    private JsonObject listResponse(JsonArray resources, long total, int start, int count) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr(LIST_URN));
        o.addProperty("totalResults", total);
        o.addProperty("startIndex", start);
        o.addProperty("itemsPerPage", resources.size());
        o.add("Resources", resources);
        return o;
    }

    private JsonObject error(int status, String scimType, String detail) {
        JsonObject o = new JsonObject();
        o.add("schemas", arr(ERROR_URN));
        if (scimType != null) o.addProperty("scimType", scimType);
        o.addProperty("detail", detail);
        o.addProperty("status", String.valueOf(status));
        return o;
    }

    private JsonObject meta(String resourceType, String id, String location) {
        JsonObject m = new JsonObject();
        m.addProperty("resourceType", resourceType);
        m.addProperty("location", location);
        return m;
    }

    private List<JsonObject> patchOps(JsonObject body) {
        req(body);
        if (!body.has("Operations") || !body.get("Operations").isJsonArray())
            throw new ScimError(400, "invalidValue", "PatchOp requires an Operations array");
        List<JsonObject> ops = new ArrayList<>();
        for (JsonElement el : body.getAsJsonArray("Operations")) {
            JsonObject op = el.getAsJsonObject();
            if (!op.has("op")) throw new ScimError(400, "invalidSyntax", "Each operation needs an 'op'");
            ops.add(op);
        }
        return ops;
    }

    private String[] parseFilter(String filter, Set<String> allowed) {
        if (filter == null || filter.isBlank()) return null;
        Matcher m = FILTER.matcher(filter.trim());
        if (!m.matches()) throw new ScimError(400, "invalidFilter", "Unsupported filter: " + filter);
        String attr = m.group(1).toLowerCase();
        if (!allowed.contains(attr)) throw new ScimError(400, "invalidFilter", "Unsupported filter attribute: " + m.group(1));
        return new String[]{attr, m.group(2)};
    }

    private void req(JsonObject body) { if (body == null) throw new ScimError(400, "invalidValue", "Request body required"); }

    private boolean asBool(JsonElement e) {
        if (e == null || e.isJsonNull()) return false;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        return "true".equalsIgnoreCase(e.getAsString());
    }

    private String optString(JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
    private String nested(JsonObject o, String a, String b) { return o.has(a) && o.get(a).isJsonObject() ? optString(o.getAsJsonObject(a), b) : null; }
    private String firstNonNull(String... v) { for (String s : v) if (s != null) return s; return null; }
    private String norm(String path) { return path == null ? "" : path.toLowerCase(); }

    private String emailFrom(JsonObject o, String current) {
        if (!o.has("emails")) return current;
        return emailValue(o.get("emails"));
    }
    private String emailValue(JsonElement emails) {
        if (emails == null || emails.isJsonNull()) return null;
        if (emails.isJsonArray()) {
            JsonArray a = emails.getAsJsonArray();
            if (a.isEmpty()) return null;
            JsonElement first = a.get(0);
            return first.isJsonObject() ? optString(first.getAsJsonObject(), "value") : first.getAsString();
        }
        return emails.isJsonObject() ? optString(emails.getAsJsonObject(), "value") : emails.getAsString();
    }

    private int intParam(Map<String,String> q, String k, int def) {
        try { return q.get(k) != null ? Integer.parseInt(q.get(k).trim()) : def; } catch (NumberFormatException e) { return def; }
    }

    private JsonArray arr(String... vals) { JsonArray a = new JsonArray(); for (String v : vals) a.add(v); return a; }
    private JsonObject feature(boolean supported) { JsonObject o = new JsonObject(); o.addProperty("supported", supported); return o; }
    private JsonObject featureMax(boolean supported) { JsonObject o = feature(supported); o.addProperty("maxOperations", 0); o.addProperty("maxPayloadSize", 0); return o; }
    private JsonObject filterFeature() { JsonObject o = feature(true); o.addProperty("maxResults", MAX_COUNT); return o; }
    private void addTs(JsonObject o, String key, Timestamp ts) { if (ts != null) o.addProperty(key, ts.toLocalDateTime().toString()); }
}
