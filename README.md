# PKI Manager v1.1

A self-contained internal Certificate Authority web application for managing a full PKI hierarchy.
Built with pure Jakarta Servlet (no framework), Bouncy Castle cryptography, and an embedded H2 database.

---

## Features

### Certificate Authority Management
Create and manage a multi-tier CA hierarchy:
- **Root CA** — self-signed, top of the trust chain
- **Intermediate CA** — signed by Root, can sign sub-CAs
- **Issuing CA** — signs end-entity certificates

Each CA stores its RSA key pair and PEM certificate in the database.
CAs can be enabled or disabled; disabled CAs cannot issue new certificates.
The CA certificate is downloadable as a PEM file for distribution.

### Certificate Issuance
Issue end-entity certificates in two ways:

**Generate key + certificate** — PKI Manager generates the RSA key pair, builds the certificate,
and stores both. The private key is available for download by authenticated users.

**Sign an external CSR** — paste a PEM-encoded PKCS#10 CSR and PKI Manager signs it
with the selected CA. Subject DN is taken from the CSR.

Supported certificate types with correct key usage extensions:
| Type | Key Usage |
|---|---|
| Server (TLS) | `digitalSignature`, `keyEncipherment` |
| Client (mTLS) | `digitalSignature`, `keyAgreement` |
| Code Signing | `digitalSignature`, `nonRepudiation` |
| Email (S/MIME) | `digitalSignature`, `keyEncipherment`, `nonRepudiation` |

Subject Alternative Names (DNS and IP) and CRL distribution points are embedded when configured.

### Certificate Revocation
Revoke any certificate via the detail page with an RFC 5280 reason code.
Revocations are written to `REVOKED_CERTIFICATE` for audit and future CRL generation.

### Public Certificate Download (token link)
Every issued certificate gets a unique UUID download token.
The link `GET /pki-manager/public/download/{token}` serves the PEM certificate
**without requiring login** — suitable for sharing with servers or clients that need
to import the certificate automatically.

The token is shown on the certificate detail page with a copy button.

### CSR Submission Portal (self-service)
External users can submit a signing request without a PKI Manager account:

1. Open `GET /pki-manager/public/csr` — paste CSR, enter name / email / notes.
2. After submission a **tracking link** is shown: `GET /pki-manager/public/csr/{token}`.
   Share this link to check status.
3. A PKI admin signs (or rejects) the request under **CSR Jobs** in the admin sidebar.
4. When signed, the tracking page shows the issued certificate and a download button.

### CSR Job Queue (admin)
`/pki-manager/admin/csr-jobs` lists all pending, signed, and rejected CSR requests.
For each pending request the admin selects:
- which CA to sign with
- the certificate type (SERVER / CLIENT / CODE_SIGNING / EMAIL)
- optional notes

Signing creates a full `CERTIFICATE_RECORD` and links it to the CSR request.

### Multi-User Authentication
Session-based login with PBKDF2WithHmacSHA256 passwords (310,000 iterations, 16-byte salt).

Two roles:
| Role | Capabilities |
|---|---|
| `ADMIN` | Full access including user management, CA creation, signing CSRs, ACME |
| `VIEWER` | Read-only access to CA list, certificate list, and dashboard |

All routes under `/dashboard`, `/ca/*`, `/cert/*`, and `/admin/*` require an active session.
Public endpoints (`/login`, `/public/download/*`, `/public/csr/*`, `/.well-known/acme-challenge/*`) are open.

### User Management
`/pki-manager/admin/users/` — full CRUD for PKI Manager accounts.
Passwords are changed separately from profile data.
An admin cannot delete their own account.

### ACME Client (Let's Encrypt)
Register a public domain and obtain a trusted TLS certificate from Let's Encrypt:

1. Open `/pki-manager/admin/acme` and register the domain.
2. Trigger a certificate request — the full ACME v2 flow runs asynchronously (~60 s):
   directory → nonce → account → order → HTTP-01 challenge → finalize → download.
3. PKI Manager serves the HTTP-01 token at `/.well-known/acme-challenge/{token}`.
4. The issued certificate and private key are stored in `ACME_CERTIFICATE`.

Both Let's Encrypt **production** and **staging** environments are supported.
EC P-256 account keys and ES256 JWS signing are used throughout the ACME flow.

### Dashboard
`/pki-manager/dashboard` shows:
- Total / valid / revoked certificate counts
- Certificates expiring within 30 days
- Recently issued certificates
- Pending CSR job count

---

## Requirements

| Component | Minimum version |
|---|---|
| Java | 21 |
| Tomcat | 10.1+ (Jakarta EE 10 / Servlet 6.x) |
| Maven | 3.8+ (for Maven build) |

Internal library dependencies (must be present in your local Maven repository or a private Maven server):
- `com.macmario:macmario-core-io:0.0.7`
- `com.macmario.services:macmario-services-PKI:1.0`
- `com.macmario.services:macmario-services-db:1.0`

---

## Build

### Maven (recommended)

```bash
mvn clean package
# produces: target/macmario-service-PKIManager-1.0.war
# or:       target/pki-manager.war (if renamed by deploy.sh)
```

### Shell script (Debian/Ubuntu, system packages)

Requires `openjdk-21-jdk` and Debian packages for Bouncy Castle 1.77, H2, SLF4J, and Logback
installed under `/usr/share/java/`.

```bash
bash build.sh
# produces: target/pki-manager.war
```

> **Note:** `build.sh` compiles with `--release 17` and uses older library versions
> (Bouncy Castle 1.77, H2 2.2.220) compared to the Maven build.
> Prefer `mvn clean package` for production builds.

---

## Deploy

### Automated

```bash
bash deploy.sh
```

The script:
1. Builds the WAR if `target/pki-manager.war` is missing.
2. Auto-detects `CATALINA_BASE` under common paths (`/var/lib/tomcat10`, `/opt/tomcat`, `/usr/local/tomcat`).
3. Copies the WAR to `$CATALINA_BASE/webapps/pki-manager.war`.
4. Prints the command to start Tomcat.

### Manual

```bash
mvn clean package
cp target/macmario-service-PKIManager-1.0.war /var/lib/tomcat10/webapps/pki-manager.war
```

Start Tomcat:
```bash
$CATALINA_HOME/bin/catalina.sh run          # foreground
# or
systemctl start tomcat10                     # systemd
```

The application is available at:
```
http://localhost:8080/pki-manager/dashboard
```

---

## First Login

On first startup PKI Manager creates a default administrator account:

| Username | Password |
|---|---|
| `admin` | `admin` |

**Change this password immediately** via *Administration → Users → admin → Change Password*.

---

## URL Reference

### Public (no login required)
| URL | Description |
|---|---|
| `GET /pki-manager/login` | Login page |
| `POST /pki-manager/login` | Authenticate |
| `POST /pki-manager/logout` | Invalidate session |
| `GET /pki-manager/public/download/{token}` | Download certificate PEM by UUID token |
| `GET /pki-manager/public/csr` | CSR submission form |
| `POST /pki-manager/public/csr` | Submit CSR |
| `GET /pki-manager/public/csr/{token}` | Check CSR request status |
| `GET /.well-known/acme-challenge/{token}` | ACME HTTP-01 challenge response |

### Dashboard
| URL | Description |
|---|---|
| `GET /pki-manager/dashboard` | Overview statistics |

### Certificate Authorities
| URL | Description |
|---|---|
| `GET /pki-manager/ca` | CA list |
| `GET /pki-manager/ca/create` | New CA form |
| `POST /pki-manager/ca/create` | Create CA (generates key + cert immediately) |
| `GET /pki-manager/ca/{id}` | CA detail, child CAs, issued certificates |
| `GET /pki-manager/ca/{id}/cert.pem` | Download CA certificate as PEM |
| `POST /pki-manager/ca/{id}/enable` | Re-enable CA |
| `POST /pki-manager/ca/{id}/disable` | Disable CA |

### Certificates
| URL | Description |
|---|---|
| `GET /pki-manager/cert` | Certificate list |
| `GET /pki-manager/cert/issue` | Issue certificate form (generate or sign CSR) |
| `POST /pki-manager/cert/issue` | Issue certificate |
| `GET /pki-manager/cert/{id}` | Certificate detail + revoke form |
| `GET /pki-manager/cert/{id}/download.pem` | Download certificate PEM (authenticated) |
| `POST /pki-manager/cert/{id}/revoke` | Revoke certificate |

### Administration
| URL | Description |
|---|---|
| `GET /pki-manager/admin/csr-jobs` | CSR job queue (pending / signed / rejected) |
| `GET /pki-manager/admin/csr-jobs/{id}` | CSR job detail + sign / reject form |
| `POST /pki-manager/admin/csr-jobs/{id}/sign` | Sign CSR with selected CA |
| `POST /pki-manager/admin/csr-jobs/{id}/reject` | Reject CSR request |
| `GET /pki-manager/admin/users/` | User list |
| `GET /pki-manager/admin/users/new` | New user form |
| `POST /pki-manager/admin/users/` | Create user |
| `GET /pki-manager/admin/users/{id}/edit` | Edit user form |
| `POST /pki-manager/admin/users/{id}` | Update user profile |
| `POST /pki-manager/admin/users/{id}/password` | Change user password |
| `POST /pki-manager/admin/users/{id}/delete` | Delete user |
| `GET /pki-manager/admin/acme` | ACME / Let's Encrypt management |
| `POST /pki-manager/admin/acme/register` | Register domain for ACME |
| `POST /pki-manager/admin/acme/{id}/request` | Trigger certificate request (async) |
| `POST /pki-manager/admin/acme/{id}/delete` | Remove ACME entry |

---

## Architecture

```
src/main/java/com/macmario/services/pki/
├── filter/
│   ├── AppStartupListener.java   WebListener — DB init, default config and admin seed
│   └── AuthFilter.java           WebFilter  — session check, redirects to /login
├── entity/                       Plain Java beans (no JPA)
│   ├── CaConfig.java
│   ├── CertificateRecord.java
│   ├── RevokedCertificate.java
│   ├── CsrRequest.java
│   ├── AcmeCertificate.java
│   └── PkiUser.java
├── service/
│   ├── CaService.java            CA CRUD + status management
│   ├── CertificateService.java   Issue, revoke, query certificates
│   ├── PkiCryptoService.java     Bouncy Castle: key gen, CA init, CSR signing
│   ├── CsrRequestService.java    CSR submission queue
│   ├── UserService.java          PBKDF2 auth, user CRUD
│   └── AcmeClientService.java    Full ACME v2 client (RFC 8555, ES256)
├── servlet/                      @WebServlet annotation-based routing
│   ├── DashboardServlet.java
│   ├── CaServlet.java
│   ├── CertificateServlet.java
│   ├── LoginServlet.java / LogoutServlet.java
│   ├── PublicDownloadServlet.java
│   ├── CsrRequestServlet.java
│   ├── CsrJobServlet.java
│   ├── UserAdminServlet.java
│   ├── AcmeMgmtServlet.java
│   └── AcmeChallengeServlet.java
└── util/
    └── EntityManagerProvider.java  Raw JDBC, H2 connection pool, schema creation
```

**Technology stack:**
- Jakarta Servlet 6.x — no Spring, no CDI
- Bouncy Castle 1.84 (`bcprov-jdk18on`, `bcpkix-jdk18on`) — all cryptography
- H2 2.4 embedded database — `AUTO_SERVER=TRUE`, persisted to disk
- Bootstrap 5.3 + Bootstrap Icons 1.11 — UI (CDN)
- Gson 2.11 — ACME JSON parsing
- `java.net.http.HttpClient` — ACME HTTP calls

---

## Database

H2 database file location (auto-created on first start):

| Environment | Path |
|---|---|
| Running under Tomcat | `$CATALINA_BASE/pki-data/pki-db` |
| Standalone / tests | `./pki-data/pki-db` |

Schema is created automatically via `EntityManagerProvider.createSchema()`.

| Table | Contents |
|---|---|
| `CA_CONFIG` | CA records: type, subject DN, PEM cert + private key, validity |
| `CERTIFICATE_RECORD` | Issued certificates: status, type, subject DN, SANs, PEM cert + optional private key, download token |
| `REVOKED_CERTIFICATE` | Revocation audit trail: reason, timestamp, operator |
| `PKI_CONFIGURATION` | Key-value runtime settings (`crl.validity.days`, `cert.expiry.warn.days`, etc.) |
| `PKI_USER` | User accounts: PBKDF2 password hash + salt, role, active flag |
| `CSR_REQUEST` | CSR submission queue: PEM, requester details, status, tracking token |
| `ACME_CERTIFICATE` | ACME-managed certificates: domain, account key, cert PEM, renewal status |
| `ACME_CHALLENGE_TOKEN` | Short-lived HTTP-01 challenge tokens served during ACME flow |

---

## Security Notes

- **Change the default admin password** (`admin` / `admin`) immediately after first login.
- Private keys are stored **unencrypted** in the H2 database. Protect the database file
  (`$CATALINA_BASE/pki-data/`) with appropriate OS file permissions.
- There is no rate limiting on the login endpoint. Place a reverse proxy (nginx, Apache)
  in front of Tomcat for production use.
- The ACME flow requires the server to be reachable from the internet on port 80 for HTTP-01
  challenges. Use staging (`Let's Encrypt Staging`) to test without hitting rate limits.
- Session timeout is 60 minutes (configurable in `LoginServlet`).
