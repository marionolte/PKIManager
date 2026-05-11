package com.macmario.services.pki.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macmario.services.pki.entity.AcmeCertificate;
import com.macmario.services.pki.util.EntityManagerProvider;
import java.io.IOException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Minimal ACME v2 client (RFC 8555) targeting Let's Encrypt.
 * Uses ES256 (ECDSA P-256) account keys.
 * HTTP-01 challenge tokens are stored in ACME_CHALLENGE_TOKEN for
 * AcmeChallengeServlet to serve at /.well-known/acme-challenge/{token}.
 */
public class AcmeClientService {
    private static final Logger log = LoggerFactory.getLogger(AcmeClientService.class);

    public static final String LE_PROD = "https://acme-v02.api.letsencrypt.org/directory";
    public static final String LE_STAGING = "https://acme-staging-v02.api.letsencrypt.org/directory";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    // ── public API ────────────────────────────────────────────────────────────

    public List<AcmeCertificate> findAll() throws SQLException {
        List<AcmeCertificate> list = new ArrayList<>();
        try (Connection c = EntityManagerProvider.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM ACME_CERTIFICATE ORDER BY domain")) {
            while (rs.next()) list.add(mapAcme(rs));
        }
        return list;
    }

    public Optional<AcmeCertificate> findById(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM ACME_CERTIFICATE WHERE id=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(mapAcme(rs)) : Optional.empty();
        }
    }

    public AcmeCertificate registerDomain(String domain, String contactEmail, boolean staging) throws GeneralSecurityException, InvalidAlgorithmParameterException, IOException, SQLException {
        // Check for existing entry
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM ACME_CERTIFICATE WHERE domain=?")) {
            ps.setString(1, domain);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapAcme(rs);
        }
        // Generate account key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("P-256"));
        KeyPair accountKey = kpg.generateKeyPair();
        String accountKeyPem = toPem(accountKey.getPrivate()) + toPem(accountKey.getPublic());

        AcmeCertificate acme = new AcmeCertificate();
        acme.setDomain(domain);
        acme.setAccountKeyPem(accountKeyPem);
        acme.setStatus(AcmeCertificate.AcmeStatus.PENDING);

        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO ACME_CERTIFICATE(domain,account_key_pem,status,auto_renew) VALUES(?,?,'PENDING',TRUE)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, domain);
            ps.setString(2, accountKeyPem);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) acme.setId(keys.getLong(1));
        }
        return acme;
    }

    /** Full ACME order flow: account → order → challenge → finalize → store cert. */
    public void requestCertificate(Long acmeId, String contactEmail, boolean staging) throws Exception {
        AcmeCertificate acme = findById(acmeId)
                .orElseThrow(() -> new IllegalArgumentException("ACME entry not found: " + acmeId));
        String directoryUrl = staging ? LE_STAGING : LE_PROD;

        try {
            // 1. Load account key
            KeyPair accountKey = loadKeyPair(acme.getAccountKeyPem());
            ECPublicKey pub = (ECPublicKey) accountKey.getPublic();

            // 2. Fetch directory
            JsonObject dir = get(directoryUrl);
            String newNonce  = dir.get("newNonce").getAsString();
            String newAcct   = dir.get("newAccount").getAsString();
            String newOrder  = dir.get("newOrder").getAsString();

            // 3. Get initial nonce
            String nonce = head(newNonce);

            // 4. Create/find account
            JsonObject acctPayload = new JsonObject();
            acctPayload.addProperty("termsOfServiceAgreed", true);
            var contacts = new com.google.gson.JsonArray();
            if (contactEmail != null && !contactEmail.isBlank())
                contacts.add("mailto:" + contactEmail);
            acctPayload.add("contact", contacts);

            JsonObject jwk = buildJwk(pub);
            HttpResponse<String> acctResp = postJws(accountKey, jwk, null, nonce, newAcct, acctPayload.toString());
            String accountUrl = acctResp.headers().firstValue("Location").orElseThrow();
            nonce = acctResp.headers().firstValue("Replay-Nonce").orElseThrow();

            // persist account URL
            updateField(acmeId, "account_url", accountUrl);
            acme.setAccountUrl(accountUrl);

            // 5. Create order
            JsonObject orderPayload = new JsonObject();
            var identifiers = new com.google.gson.JsonArray();
            JsonObject id = new JsonObject(); id.addProperty("type","dns"); id.addProperty("value", acme.getDomain());
            identifiers.add(id);
            orderPayload.add("identifiers", identifiers);

            HttpResponse<String> orderResp = postJws(accountKey, null, accountUrl, nonce, newOrder, orderPayload.toString());
            String orderUrl = orderResp.headers().firstValue("Location").orElseThrow();
            nonce = orderResp.headers().firstValue("Replay-Nonce").orElseThrow();
            JsonObject order = JsonParser.parseString(orderResp.body()).getAsJsonObject();

            // 6. Get authorization and HTTP-01 challenge
            String authzUrl = order.getAsJsonArray("authorizations").get(0).getAsString();
            HttpResponse<String> authzResp = postJws(accountKey, null, accountUrl, nonce, authzUrl, "");
            nonce = authzResp.headers().firstValue("Replay-Nonce").orElseThrow();
            JsonObject authz = JsonParser.parseString(authzResp.body()).getAsJsonObject();

            JsonObject challenge = null;
            for (JsonElement el : authz.getAsJsonArray("challenges")) {
                if ("http-01".equals(el.getAsJsonObject().get("type").getAsString())) {
                    challenge = el.getAsJsonObject();
                    break;
                }
            }
            if (challenge == null) throw new RuntimeException("No HTTP-01 challenge offered by ACME server");

            String token = challenge.get("token").getAsString();
            String keyAuth = token + "." + jwkThumbprint(pub);
            String challengeUrl = challenge.get("url").getAsString();

            // 7. Store challenge token for HTTP-01 servlet
            storeChallenge(token, keyAuth);

            // 8. Signal LE to verify
            HttpResponse<String> challResp = postJws(accountKey, null, accountUrl, nonce, challengeUrl, "{}");
            nonce = challResp.headers().firstValue("Replay-Nonce").orElseThrow();

            // 9. Poll until authorization is valid (max 60s)
            for (int i = 0; i < 12; i++) {
                Thread.sleep(5000);
                HttpResponse<String> pollResp = postJws(accountKey, null, accountUrl, nonce, authzUrl, "");
                nonce = pollResp.headers().firstValue("Replay-Nonce").orElseThrow();
                JsonObject pollBody = JsonParser.parseString(pollResp.body()).getAsJsonObject();
                String st = pollBody.get("status").getAsString();
                if ("valid".equals(st)) break;
                if ("invalid".equals(st)) throw new RuntimeException("ACME authorization failed: " + pollBody);
            }

            // 10. Generate domain key pair and CSR
            KeyPairGenerator domainKpg = KeyPairGenerator.getInstance("RSA");
            domainKpg.initialize(4096);
            KeyPair domainKey = domainKpg.generateKeyPair();

            PKCS10CertificationRequest csr = new JcaPKCS10CertificationRequestBuilder(
                    new X500Name("CN=" + acme.getDomain()), domainKey.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(domainKey.getPrivate()));
            byte[] csrDer = csr.getEncoded();

            // 11. Finalize
            String finalizeUrl = order.get("finalize").getAsString();
            JsonObject finalPayload = new JsonObject();
            finalPayload.addProperty("csr", Base64.getUrlEncoder().withoutPadding().encodeToString(csrDer));
            HttpResponse<String> finalResp = postJws(accountKey, null, accountUrl, nonce, finalizeUrl, finalPayload.toString());
            nonce = finalResp.headers().firstValue("Replay-Nonce").orElseThrow();

            // 12. Poll order until certificate is ready
            String certUrl = null;
            for (int i = 0; i < 12; i++) {
                Thread.sleep(5000);
                HttpResponse<String> pollResp = postJws(accountKey, null, accountUrl, nonce, orderUrl, "");
                nonce = pollResp.headers().firstValue("Replay-Nonce").orElseThrow();
                JsonObject pollOrder = JsonParser.parseString(pollResp.body()).getAsJsonObject();
                if ("valid".equals(pollOrder.get("status").getAsString())) {
                    certUrl = pollOrder.get("certificate").getAsString();
                    break;
                }
            }
            if (certUrl == null) throw new RuntimeException("Order did not become valid in time");

            // 13. Download certificate chain
            HttpResponse<String> certResp = postJws(accountKey, null, accountUrl, nonce, certUrl, "");
            String chain = certResp.body();

            // Split PEM chain: first cert is leaf, rest is chain
            String[] parts = chain.split("(?<=-----END CERTIFICATE-----)");
            String leafPem = parts[0].trim();
            StringBuilder chainPem = new StringBuilder();
            for (int i = 1; i < parts.length; i++) chainPem.append(parts[i].trim()).append("\n");

            // Persist
            String domainKeyPem = toPem(domainKey.getPrivate());
            try (Connection c = EntityManagerProvider.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE ACME_CERTIFICATE SET cert_pem=?,chain_pem=?,private_key_pem=?," +
                     "status='ACTIVE',last_renewed_at=?,valid_from=CURRENT_TIMESTAMP,error_message=NULL WHERE id=?")) {
                ps.setString(1, leafPem);
                ps.setString(2, chainPem.toString());
                ps.setString(3, domainKeyPem);
                ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                ps.setLong(5, acmeId);
                ps.executeUpdate();
            }

            log.info("ACME certificate obtained for domain {}", acme.getDomain());
        } catch (IOException | GeneralSecurityException | InterruptedException | SQLException | IllegalArgumentException e) {
            try (Connection c = EntityManagerProvider.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE ACME_CERTIFICATE SET status='ERROR',error_message=? WHERE id=?")) {
                ps.setString(1, e.getMessage());
                ps.setLong(2, acmeId);
                ps.executeUpdate();
            }
            throw e;
        } finally {
            cleanChallenge();
        }
    }

    public void delete(Long id) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ACME_CERTIFICATE WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    // ── challenge token management ────────────────────────────────────────────

    public static String getChallengeKeyAuth(String token) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT key_auth FROM ACME_CHALLENGE_TOKEN WHERE token=?")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("key_auth") : null;
        }
    }

    private void storeChallenge(String token, String keyAuth) throws SQLException {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "MERGE INTO ACME_CHALLENGE_TOKEN(token,key_auth) KEY(token) VALUES(?,?)")) {
            ps.setString(1, token);
            ps.setString(2, keyAuth);
            ps.executeUpdate();
        }
    }

    private void cleanChallenge() {
        try (Connection c = EntityManagerProvider.getConnection();
             Statement st = c.createStatement()) {
            st.execute("DELETE FROM ACME_CHALLENGE_TOKEN");
        } catch (Exception ignored) {}
    }

    // ── JWS helpers ───────────────────────────────────────────────────────────

    private HttpResponse<String> postJws(KeyPair key, JsonObject jwk, String kid, String nonce,
                                          String url, String payload) throws Exception {
        String payloadB64 = payload.isEmpty() ? "" : b64url(payload.getBytes(StandardCharsets.UTF_8));

        JsonObject header = new JsonObject();
        header.addProperty("alg", "ES256");
        header.addProperty("nonce", nonce);
        header.addProperty("url", url);
        if (jwk != null) header.add("jwk", jwk);
        else             header.addProperty("kid", kid);

        String headerB64 = b64url(header.toString().getBytes(StandardCharsets.UTF_8));
        String sigInput  = headerB64 + "." + payloadB64;
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(key.getPrivate());
        sig.update(sigInput.getBytes(StandardCharsets.UTF_8));
        byte[] derSig = sig.sign();
        byte[] joseP1363 = derToP1363(derSig);

        JsonObject body = new JsonObject();
        body.addProperty("protected", headerB64);
        body.addProperty("payload", payloadB64);
        body.addProperty("signature", b64url(joseP1363));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/jose+json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** Convert DER-encoded ECDSA signature to fixed-size P1363 (r||s, 32+32 bytes). */
    private byte[] derToP1363(byte[] der) {
        int idx = 2;
        int rLen = der[idx + 1] & 0xff; idx += 2;
        byte[] r = Arrays.copyOfRange(der, idx, idx + rLen); idx += rLen;
        int sLen = der[idx + 1] & 0xff; idx += 2;
        byte[] s = Arrays.copyOfRange(der, idx, idx + sLen);
        byte[] out = new byte[64];
        copyRight(r, out, 0, 32);
        copyRight(s, out, 32, 32);
        return out;
    }

    private void copyRight(byte[] src, byte[] dst, int dstOff, int len) {
        int srcOff = Math.max(0, src.length - len);
        int copyLen = src.length - srcOff;
        System.arraycopy(src, srcOff, dst, dstOff + (len - copyLen), copyLen);
    }

    private JsonObject buildJwk(ECPublicKey pub) {
        byte[] x = pub.getW().getAffineX().toByteArray();
        byte[] y = pub.getW().getAffineY().toByteArray();
        JsonObject jwk = new JsonObject();
        jwk.addProperty("crv", "P-256");
        jwk.addProperty("kty", "EC");
        jwk.addProperty("x", b64url(pad32(x)));
        jwk.addProperty("y", b64url(pad32(y)));
        return jwk;
    }

    private String jwkThumbprint(ECPublicKey pub) throws Exception {
        // Canonical form: sorted keys {crv,kty,x,y}
        byte[] x = pub.getW().getAffineX().toByteArray();
        byte[] y = pub.getW().getAffineY().toByteArray();
        String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" +
                b64url(pad32(x)) + "\",\"y\":\"" + b64url(pad32(y)) + "\"}";
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return b64url(sha256.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] pad32(byte[] b) {
        if (b.length == 32) return b;
        if (b.length == 33 && b[0] == 0) return Arrays.copyOfRange(b, 1, 33);
        byte[] out = new byte[32];
        System.arraycopy(b, Math.max(0, b.length - 32), out, Math.max(0, 32 - b.length), Math.min(b.length, 32));
        return out;
    }

    private String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private JsonObject get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Accept", "application/json").GET().build();
        return JsonParser.parseString(http.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    private String head(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).headers()
                .firstValue("Replay-Nonce").orElseThrow();
    }

    // ── key loading ───────────────────────────────────────────────────────────

    private KeyPair loadKeyPair(String pem) throws Exception {
        try (org.bouncycastle.openssl.PEMParser parser = new org.bouncycastle.openssl.PEMParser(new java.io.StringReader(pem))) {
            PrivateKey priv = null; PublicKey pub = null;
            Object obj;
            org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter conv =
                    new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter();
            while ((obj = parser.readObject()) != null) {
                if (obj instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                    priv = conv.getPrivateKey(kp.getPrivateKeyInfo());
                    pub  = conv.getPublicKey(kp.getPublicKeyInfo());
                } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                    priv = conv.getPrivateKey(pki);
                } else if (obj instanceof org.bouncycastle.asn1.x509.SubjectPublicKeyInfo spki) {
                    pub = conv.getPublicKey(spki);
                }
            }
            if (priv == null) throw new RuntimeException("No private key in PEM");
            if (pub == null) {
                // Derive public from private for EC
                KeyFactory kf = KeyFactory.getInstance("EC");
                java.security.spec.ECPrivateKeySpec privSpec = kf.getKeySpec(priv, java.security.spec.ECPrivateKeySpec.class);
                // Use BC provider approach
                org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
                java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC", bc);
                // Can't easily derive—store both in PEM
                throw new RuntimeException("PEM must contain both private and public key");
            }
            return new KeyPair(pub, priv);
        }
    }

    private String toPem(Object obj) throws IOException  {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) { w.writeObject(obj); }
        return sw.toString();
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private void updateField(Long id, String col, String val) throws SQLException  {
        try (Connection c = EntityManagerProvider.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE ACME_CERTIFICATE SET " + col + "=? WHERE id=?")) {
            ps.setString(1, val);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private AcmeCertificate mapAcme(ResultSet rs) throws SQLException {
        AcmeCertificate a = new AcmeCertificate();
        a.setId(rs.getLong("id"));
        a.setDomain(rs.getString("domain"));
        a.setAccountUrl(rs.getString("account_url"));
        a.setAccountKeyPem(rs.getString("account_key_pem"));
        a.setCertPem(rs.getString("cert_pem"));
        a.setChainPem(rs.getString("chain_pem"));
        a.setPrivateKeyPem(rs.getString("private_key_pem"));
        Timestamp vf = rs.getTimestamp("valid_from"); if (vf != null) a.setValidFrom(vf.toLocalDateTime());
        Timestamp vu = rs.getTimestamp("valid_until"); if (vu != null) a.setValidUntil(vu.toLocalDateTime());
        a.setAutoRenew(rs.getBoolean("auto_renew"));
        Timestamp lr = rs.getTimestamp("last_renewed_at"); if (lr != null) a.setLastRenewedAt(lr.toLocalDateTime());
        a.setStatus(AcmeCertificate.AcmeStatus.valueOf(rs.getString("status")));
        a.setErrorMessage(rs.getString("error_message"));
        Timestamp ca = rs.getTimestamp("created_at"); if (ca != null) a.setCreatedAt(ca.toLocalDateTime());
        return a;
    }
}
