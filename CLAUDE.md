# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Standard Maven build (produces target/pki-manager.war)
mvn clean package

# Alternative shell-based build (uses system JARs, see build.sh for paths)
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
| Servlets | `filter/` | `DashboardServlet`, `CaServlet`, `CertificateServlet` — annotation-based routing |
| Services | `services/` | `CaService`, `CertificateService`, `PkiCryptoService` |
| Persistence | `persistence/` | Raw JDBC + H2; `EntityManagerProvider` owns connection pool and schema init |
| Views | `src/main/webapp/WEB-INF/views/*.jsp` | JSP; direct access blocked by web.xml |

`AppStartupListener` (ServletContextListener) bootstraps the database and seeds default `PKI_CONFIGURATION` entries on startup.

## Database

**H2 embedded**, stored at `${catalina.base}/pki-data/pki-db` (or `./pki-data/pki-db` locally). Schema is auto-created on first run. Four tables:

- `CA_CONFIG` — Certificate Authority records (ROOT / INTERMEDIATE / ISSUING), stores PEM cert + private key
- `CERTIFICATE_RECORD` — Issued certs with status (VALID / REVOKED / EXPIRED), stores PEM cert + optional private key + CSR
- `REVOKED_CERTIFICATE` — Revocation audit trail with reason codes
- `PKI_CONFIGURATION` — Key-value runtime settings (e.g. `crl.validity.days`, `cert.expiry.warn.days`)

H2 URL uses `AUTO_SERVER=TRUE`, allowing multiple simultaneous connections.

## Cryptography

All crypto goes through **`PkiCryptoService`** using **Bouncy Castle 1.84** (`bcprov`, `bcpkix`). Key operations:
- RSA key pair generation (configurable size, default 4096 for CAs, 2048 for leaf certs)
- Self-signed Root CA generation
- Sub-CA / Issuing CA signing (chains parent CA)
- CSR signing (external PKCS#10 or internally generated)
- Certificate types: `SERVER`, `CLIENT`, `CODE_SIGNING`, `EMAIL`, `CA`
- Extensions: SAN (DNS + IP), CRL DP, OCSP URL

Private keys are stored in PEM format in the H2 database alongside their certificates.

## Internal Dependencies

Two internal libraries resolved from Maven (must be in local `~/.m2` or a private repo):
- `com.macmario:macmario-core-io:0.0.7`
- `com.macmario:macmario-services-PKI:1.0`
