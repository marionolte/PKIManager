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

`AppStartupListener` (in package `filter/`, implements `WebListener`) bootstraps the database, seeds default `PKI_CONFIGURATION` entries on startup, and runs the daily auto-backup scheduler (a daemon `ScheduledExecutorService` that fires once a day at `backup.auto.time`; stopped before DB close).

**Backup & Restore** (`BackupService`, `BackupServlet` at `/admin/backup/*`, admin-only): full DB export/import as **JSON** (never executable SQL). Import is a single rolled-back transaction, validates the file (format, known tables, ≥1 active ADMIN), writes a `pre-import` safety copy, and realigns identity sequences afterward. Auto-backup runs once a day at `backup.auto.time` (default 22:05) and writes `auto-<ts>.json` only when a content fingerprint (excluding volatile columns like `last_used_at`/`last_login_at`) changes. `ConfigService` reads/writes `PKI_CONFIGURATION` (`backup.auto.enabled|time|keep`).

## URL Routing

All servlet routes use `@WebServlet` annotations (no web.xml mappings):

| Servlet | URL pattern | Key routes |
|---|---|---|
| `DashboardServlet` | `/dashboard`, `/` | GET: stats + expiring certs |
| `CaServlet` | `/ca/*` | GET `/ca/`, `/ca/create`, `/ca/import`, `/ca/{id}`, `/ca/{id}/cert.pem`; POST `/ca/create`, `/ca/import` (admin), `/ca/{id}/enable`, `/ca/{id}/disable`, `/ca/{id}/revoke` (admin), `/ca/{id}/delete` (admin, cascade) |
| `CertificateServlet` | `/cert/*` | GET `/cert/`, `/cert/issue`, `/cert/{id}`, `/cert/{id}/download.pem`; POST `/cert/issue`, `/cert/{id}/revoke` |
| `MyApiClientServlet` | `/api-clients/*` | any signed-in user; GET `/`; POST `/request`, `/{id}/rotate|enable|disable|delete` (own only) |
| `ApiClientAdminServlet` | `/admin/api-clients/*` | admin-only; adds POST `/{id}/approve|reject` (same-origin checked) |
| `ScimServlet` | `/scim/v2/*` | SCIM 2.0; bearer-token auth (`ScimAuthFilter`); GET(list/id)/PUT/PATCH for `/Users`, `/Groups` (roles), `/Certificates` (revoke), `/ApiClients`, plus discovery. Logic in `ScimService` |
| `DocsServlet` | `/docs/*` | in-app online documentation (login required); topic allowlist → fragment under `/WEB-INF/views/docs/`, rendered by `docs.jsp`. Topics: certificates, scim, api-clients, acme |

## Database

**H2 embedded**, stored at `${catalina.base}/pki-data/pki-db` (falls back to `./pki-data/pki-db`). Schema is auto-created on first run. Four tables:

- `CA_CONFIG` — Certificate Authority records (ROOT / INTERMEDIATE / ISSUING), stores PEM cert + private key
- `CERTIFICATE_RECORD` — Issued certs with status (VALID / REVOKED / EXPIRED), stores PEM cert + optional private key + CSR
- `REVOKED_CERTIFICATE` — Revocation audit trail with RFC 5280 reason codes
- `PKI_CONFIGURATION` — Key-value runtime settings (e.g. `crl.validity.days`, `cert.expiry.warn.days`)
- `API_CLIENT` — REST API clients with an **owner** (`owner_user_id`) and **approval** workflow (`approval_status` PENDING/APPROVED/REJECTED, `approved_by`). Non-admins request clients (self-service `/api-clients/`); an admin approves them before the key works. `ApiClientService.authenticate` is the single auth gate (active AND approved AND owner active/null) used by `ApiAuthFilter`, which also records last access (`last_used_at`, `last_ip`, `last_user_agent`)

H2 URL uses `AUTO_SERVER=TRUE`, allowing multiple simultaneous connections.

## Cryptography

All crypto goes through **`PkiCryptoService`** using **Bouncy Castle 1.84** (`bcprov`, `bcpkix`). Key operations:
- RSA key pair generation (configurable size, default 4096 for CAs, 2048 for leaf certs)
- Self-signed Root CA generation (`initRootCa`)
- Sub-CA / Issuing CA signing — chains parent CA signature (`initSubCa`)
- **Import** existing Root/Sub CA from PEM cert + RSA key (`importCa`) — validates key↔cert match, CA basic constraints / `keyCertSign`, type consistency (self-signed ⇒ ROOT), optional parent chain verification; supports encrypted keys (PKCS#8 / PKCS#1) with a password; extracts subject, serial, validity, key size, digest (`default_md`), CRL/OCSP URLs and Name Constraints from the cert (not entered on the form)
- **Issuer DN for signing is read from the CA's own certificate** (`issuerNameOf`), not rebuilt from DN fields — required so imported CAs with non-trivial DNs (DC components, multiple OUs) produce chains that validate
- External PKCS#10 CSR signing (`signCsr`) or generate-and-sign in one call (`generateAndSign`)
- Certificate types: `SERVER`, `CLIENT`, `CODE_SIGNING`, `EMAIL`, `CA` — each maps to distinct key usage bits
- Extensions: SAN (DNS + IP), CRL DP, OCSP URL
- **Name Constraints**: Intermediate/Issuing CAs can carry a critical `permittedSubtrees` extension (`CaConfig.permittedDomains`, e.g. `int`) restricting which DNS/e-mail domains they may issue for. Root CAs ignore it.

**CA lifecycle:** CAs can be disabled/enabled, **revoked**, or **deleted** (both admin-only). Revocation (`CaService.revoke`) is permanent, cascades to all descendant CAs, and revokes every VALID cert in the subtree via `REVOKED_CERTIFICATE`. Deletion (`CaService.delete`) permanently removes the CA, all descendant CAs, their issued certs + revocation rows, and detaches CSR/API-client references — all in one transaction. Only ACTIVE, non-expired CAs may issue certs (enforced server-side in `CertificateService`).

Private keys are stored unencrypted in PEM format in H2. There is no authentication layer — access control must be added at the Tomcat realm or reverse proxy level.

**SCIM 2.0** (`ScimService` + `ScimServlet` at `/scim/v2/*`, `ScimAuthFilter`): identity/resource provisioning for Users, Groups (roles ADMIN/VIEWER, membership-driven), Certificates (revoke-only) and ApiClients. Auth is a static bearer token whose SHA-256 lives in `PKI_CONFIGURATION` (`scim.token.sha256`; generate/rotate on the Users admin page). Responses are hand-built JSON — never entity serialization — so hashes/keys never leak. Role changes/deactivation take effect immediately because `AuthFilter` reloads the session user each request. Last-active-admin is protected on every path.

## Internal Dependencies

Two internal libraries resolved from Maven (must be in local `~/.m2` or a private repo):
- `com.macmario:macmario-core-io:0.0.7`
- `com.macmario:macmario-services-PKI:1.0`

## Notes

- `example/` contains two reference variants of the app (one with ACME support); these are not built by the main `pom.xml`.
- `build.sh` compiles with `--release 17` and references system-package JARs (Bouncy Castle 1.77, H2 2.2.220) — older than the pom.xml versions. Maven build is preferred.
