# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Standard Maven build (produces target/macmario-service-PKIManager-1.0.war)
mvn clean package

# Alternative shell-based build (uses Debian/Ubuntu system JARs — paths in build.sh)
bash build.sh
```

**Deploy to Tomcat:**
```bash
bash deploy.sh
# App available at: http://localhost:8080/pki-manager/dashboard
```

**Requirements:** Java 21+, Tomcat 10+ (Jakarta EE 10 / Servlet 6.x)

No test suite exists — `src/test` is empty.

## Architecture

Pure **Jakarta Servlet** web app (no Spring). Three-layer structure:

| Layer | Location | Notes |
|---|---|---|
| Servlets | `servlet/` | `DashboardServlet`, `CaServlet`, `CertificateServlet` — annotation-based routing |
| Services | `service/` | `CaService`, `CertificateService`, `PkiCryptoService` |
| Persistence | `util/EntityManagerProvider` | Raw JDBC + H2; owns connection pool and schema init |
| Views | `src/main/webapp/WEB-INF/views/*.jsp` | JSP; direct access blocked by web.xml |

`AppStartupListener` (in package `filter/`, implements `WebListener`) bootstraps the database and seeds default `PKI_CONFIGURATION` entries on startup.

## URL Routing

All servlet routes use `@WebServlet` annotations (no web.xml mappings):

| Servlet | URL pattern | Key routes |
|---|---|---|
| `DashboardServlet` | `/dashboard`, `/` | GET: stats + expiring certs |
| `CaServlet` | `/ca/*` | GET `/ca/`, `/ca/create`, `/ca/{id}`, `/ca/{id}/cert.pem`; POST `/ca/create`, `/ca/{id}/enable`, `/ca/{id}/disable` |
| `CertificateServlet` | `/cert/*` | GET `/cert/`, `/cert/issue`, `/cert/{id}`, `/cert/{id}/download.pem`; POST `/cert/issue`, `/cert/{id}/revoke` |

## Database

**H2 embedded**, stored at `${catalina.base}/pki-data/pki-db` (falls back to `./pki-data/pki-db`). Schema is auto-created on first run. Four tables:

- `CA_CONFIG` — Certificate Authority records (ROOT / INTERMEDIATE / ISSUING), stores PEM cert + private key
- `CERTIFICATE_RECORD` — Issued certs with status (VALID / REVOKED / EXPIRED), stores PEM cert + optional private key + CSR
- `REVOKED_CERTIFICATE` — Revocation audit trail with RFC 5280 reason codes
- `PKI_CONFIGURATION` — Key-value runtime settings (e.g. `crl.validity.days`, `cert.expiry.warn.days`)

H2 URL uses `AUTO_SERVER=TRUE`, allowing multiple simultaneous connections.

## Cryptography

All crypto goes through **`PkiCryptoService`** using **Bouncy Castle 1.84** (`bcprov`, `bcpkix`). Key operations:
- RSA key pair generation (configurable size, default 4096 for CAs, 2048 for leaf certs)
- Self-signed Root CA generation (`initRootCa`)
- Sub-CA / Issuing CA signing — chains parent CA signature (`initSubCa`)
- External PKCS#10 CSR signing (`signCsr`) or generate-and-sign in one call (`generateAndSign`)
- Certificate types: `SERVER`, `CLIENT`, `CODE_SIGNING`, `EMAIL`, `CA` — each maps to distinct key usage bits
- Extensions: SAN (DNS + IP), CRL DP, OCSP URL

Private keys are stored unencrypted in PEM format in H2. There is no authentication layer — access control must be added at the Tomcat realm or reverse proxy level.

## Internal Dependencies

Two internal libraries resolved from Maven (must be in local `~/.m2` or a private repo):
- `com.macmario:macmario-core-io:0.0.7`
- `com.macmario:macmario-services-PKI:1.0`

## Notes

- `example/` contains two reference variants of the app (one with ACME support); these are not built by the main `pom.xml`.
- `build.sh` compiles with `--release 17` and references system-package JARs (Bouncy Castle 1.77, H2 2.2.220) — older than the pom.xml versions. Maven build is preferred.
