# PKI Manager v1.4

A self-contained internal Certificate Authority web application for managing a full PKI hierarchy.
Built with pure Jakarta Servlet (no framework), Bouncy Castle cryptography, and an embedded H2 database.

**At a glance:**

- **CA hierarchy** — create Root / Intermediate / Issuing CAs, **import** existing CAs (cert + key),
  and revoke or delete them (cascading), with optional X.509 Name Constraints.
- **Certificates** — issue (generated key or external CSR), download, revoke; self-service CSR portal.
- **REST API** — per-application API keys with an owner + **admin-approval** workflow.
- **SCIM 2.0** — provision Users, roles (Groups), Certificates and API Clients from an IdP.
- **ACME v2** — obtain publicly-trusted TLS certificates from Let's Encrypt.
- **Backup & Restore** — full JSON export/import plus a daily change-triggered auto-backup.
- **Online documentation** — in-app guides for Certificates, SCIM, API Clients and ACME.
- **Multi-user** — session login with ADMIN / VIEWER roles (PBKDF2 passwords).

---

## Features

### Certificate Authority Management
Create and manage a multi-tier CA hierarchy:
- **Root CA** — self-signed, top of the trust chain
- **Intermediate CA** — signed by Root, can sign sub-CAs
- **Issuing CA** — signs end-entity certificates

Each CA stores its RSA key pair and PEM certificate in the database.
The CA certificate is downloadable as a PEM file for distribution.

**Import an existing CA** *(admin)* — instead of generating a new key, import a Root or Sub CA by
pasting its **certificate** (single cert or full chain) and **RSA private key** (PEM). PKI Manager:
- verifies the private key matches the certificate,
- checks the certificate is a usable CA (`basicConstraints CA:TRUE`, `keyCertSign` if key usage is present),
- enforces type consistency (a self-signed cert must be imported as ROOT; a sub CA must not be self-signed),
- optionally verifies issuer + signature against a selected parent CA already in the system,
- reads subject DN, serial, validity, key size, signature digest, CRL / OCSP URLs and any existing
  Name Constraints straight from the certificate (so those fields aren't entered on the form),
- supports **encrypted** keys (PKCS#8 or traditional PKCS#1) via an optional key password.

The key is stored unencrypted (same model as generated CAs). Certificates issued by an imported CA use
the **exact issuer DN from the imported certificate**, so chains validate even when the DN uses DC
components, multiple OUs or unusual attribute ordering.

**CA lifecycle (Root and Sub CAs alike):**
- **Enable / disable** — a disabled CA cannot issue new certificates.
- **Revoke** *(admin)* — permanently marks the CA `REVOKED`, **cascades to every descendant CA**,
  and revokes all still-valid certificates in the subtree (written to `REVOKED_CERTIFICATE`).
  A revoked CA can never be re-enabled.
- **Delete** *(admin)* — permanently removes the CA, all descendant CAs, every certificate they
  issued (plus revocation records), and detaches CSR / API-client references — all in one transaction.
  Irreversible.

Only **ACTIVE, non-expired** CAs may issue certificates; this is enforced server-side on every
issuance path (web UI, API, and CSR job signing).

> **Note:** the application does not generate CRLs, so CA revocation is a database-level trust
> marker. A revoked Root in particular must be removed from client trust stores manually.

**Name Constraints (internal CAs):** when creating an Intermediate or Issuing CA you can specify
one or more **permitted domains** (e.g. `int`). PKI Manager then embeds a critical X.509
`NameConstraints` extension (`permittedSubtrees`) so the CA is cryptographically restricted to
issuing certificates for hosts and e-mail addresses under those domains only —
`int` permits `host.int` (DNS) and mailboxes in the `.int` domain. Root CAs ignore this field.

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
| `VIEWER` | Read-only access to CA list, certificate list, and dashboard; may **request** API clients (self-service) and manage their own once approved |

All routes under `/dashboard`, `/ca/*`, `/cert/*`, `/admin/*`, `/api-clients/*`, and `/docs/*` require an
active session (`/api-clients/*` and `/docs/*` are open to any signed-in user; `/admin/*` is admin-only).
Administrative actions are additionally **ADMIN-only**, enforced server-side: user management
(`/admin/users/*`), API-client management (`/admin/api-clients/*`), backup & restore
(`/admin/backup/*`), and CA import/revoke/delete return `403` for VIEWERs.
The machine APIs use their own credentials instead of a session: `/api/*` requires an `X-API-Key`
header (approved API client) and `/scim/*` requires an `Authorization: Bearer` token.
Public endpoints (`/login`, `/public/download/*`, `/public/csr/*`, `/.well-known/acme-challenge/*`) are open.

### User Management
`/pki-manager/admin/users/` — full CRUD for PKI Manager accounts (**ADMIN only**).
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

### Application API (API Clients)
External applications can integrate with PKI Manager via a REST API using per-application API keys.
No user session is required — authentication is done with a static key passed in the `X-API-Key` header.

**Isolation:** each API client can only see and download its own certificates.
CA management (create, enable, disable) is fully blocked for API clients.

**Access tracking:** every authenticated API call records the client's last access time, remote IP
(`request.getRemoteAddr()`) and `User-Agent`. On the *API Clients* admin page, the <i>Access info</i>
button on each row shows these details. Behind a reverse proxy, configure Tomcat's `RemoteIpValve`
with the proxy as a trusted source so `getRemoteAddr()` returns the real client address — client-sent
`X-Forwarded-For` headers are intentionally **not** trusted directly (they are forgeable).

**curl examples:** the *API Clients* page includes ready-to-copy `curl` snippets for every endpoint,
with the base URL auto-detected from the current request.

#### Request & approval workflow
API clients are **owned** by the user who creates them and carry an approval status:

- Any signed-in user (including a **VIEWER**) can **request** a client at `/pki-manager/api-clients/`.
  The request is created **PENDING** and inactive — its key does not work yet.
- An **ADMIN** must **approve** it (Administration → *API Clients (admin)*, `/admin/api-clients/`).
  Approval activates the client; rejection disables it permanently.
- After approval the owner manages **only their own** clients (reveal/rotate key, enable/disable, delete)
  from `/pki-manager/api-clients/`. Admins who create a client directly get it auto-approved.
- Authentication is gated centrally (`ApiClientService.authenticate`): a key works only when the client is
  **active, APPROVED, and its owner is still active** (or has no owner). Approve/reject and all self-service
  actions are protected by a same-origin (CSRF) check on the `Origin`/`Referer` host.
- **Approval grants broad issuance:** an approved client may issue from **any active CA** by passing `caId`
  in the request; the "Preferred Issuing CA" is only the default when `caId` is omitted. Approve accordingly.

#### Setting up an API client
1. Open *Administration → API Clients* and click **New API Client**.
2. Enter a name, optional description, and optionally pre-select a default issuing CA.
3. After creation the full API key is shown **once** — copy it immediately.
4. Pass the key on every API request: `X-API-Key: pki_<key>`

#### Issuing a certificate (server generates key pair)
```http
POST /pki-manager/api/v1/certs
X-API-Key: pki_<key>
Content-Type: application/json

{
  "commonName": "app.example.com",
  "certType": "SERVER",
  "sanDns": "app.example.com,www.example.com",
  "organization": "ACME Corp",
  "keySize": 2048,
  "caId": 3
}
```
Response `201` includes `certificatePem` and `privateKeyPem` (only returned at creation).

#### Auto-signing an external CSR
```http
POST /pki-manager/api/v1/certs/sign
X-API-Key: pki_<key>
Content-Type: application/json

{
  "csrPem": "-----BEGIN CERTIFICATE REQUEST-----\n...",
  "certType": "SERVER",
  "caId": 3
}
```
The CSR is signed immediately and a `CERTIFICATE_RECORD` is returned.
The signing is also recorded in the CSR job queue for audit purposes.

#### API endpoints
All endpoints require `X-API-Key` header. Base path: `/pki-manager/api/v1/`

| Method | Path | Description |
|---|---|---|
| `GET` | `/cas` | List active issuing CAs (for reference) |
| `GET` | `/certs` | List own certificates |
| `POST` | `/certs` | Issue certificate (PKI Manager generates key pair) |
| `POST` | `/certs/sign` | Auto-sign external CSR, returns certificate immediately |
| `GET` | `/certs/{id}` | Certificate details (own only) |
| `GET` | `/certs/{id}/pem` | Download certificate PEM (own only) |
| `GET` | `/csr` | List own CSR signing jobs |
| `GET` | `/csr/{id}` | CSR job status (own only) |

All responses are JSON. Errors return `{"error": "message"}` with an appropriate HTTP status code.

If a default CA is configured on the API client, `caId` can be omitted from requests.

### SCIM 2.0 Provisioning
A [SCIM 2.0](https://datatracker.ietf.org/doc/html/rfc7644) endpoint at `/pki-manager/scim/v2/` lets an
identity provider (Entra ID, Okta, …) or scripts manage identities and resources:

| Resource | Endpoint | Maps to | Operations |
|---|---|---|---|
| **Users** | `/Users` | `PKI_USER` | list, get, PUT, PATCH |
| **Groups** | `/Groups` | roles `ADMIN` / `VIEWER` | list, get, PUT, PATCH (membership = role) |
| **Certificates** | `/Certificates` | `CERTIFICATE_RECORD` | list, get, PATCH (revoke only) |
| **ApiClients** | `/ApiClients` | `API_CLIENT` | list, get, PUT, PATCH (active/approval) |

Plus discovery: `/ServiceProviderConfig`, `/ResourceTypes`, `/Schemas`.

- **Roles as Groups:** `role` is single-valued, so ADMIN membership is authoritative and VIEWER is its
  complement. Adding a user to ADMIN promotes them; removing them demotes to VIEWER. Any change that would
  leave **no active administrator** is refused (400).
- **Deprovisioning is immediate:** `AuthFilter` reloads the user each request, so a SCIM `active=false` or
  role change ends the affected web session on the next request instead of after the timeout.
- **Certificates** are read-only except a PATCH of `status` to `REVOKED` (which writes a `REVOKED_CERTIFICATE`
  row); un-revoking is rejected. **ApiClients** route through the same guards as the UI (approval required to
  activate; owned clients are sticky-revoked). No response ever includes a secret (password hash, private key,
  CSR, API key or download token).
- **Auth:** a static **bearer token** (`Authorization: Bearer <token>`), separate from sessions and
  API-client keys. Generate/rotate it on the *Users* admin page (only its SHA-256 is stored; shown once). If
  no token is set, SCIM returns 401 (disabled). Filtering supports `attr eq "value"`; pagination uses
  `startIndex`/`count`.
- **Not yet implemented:** `POST /Users` (create) and `DELETE`. Entra ID / Okta provisioning that creates
  users after a filter-miss would need `POST /Users` — a straightforward follow-up.

### Backup & Restore (admin)
`/pki-manager/admin/backup/` lets an admin export and import the **entire** database:

- **Export** — download a complete JSON backup of every table (CAs, certificates, revocations,
  users, API clients, CSR jobs, ACME data, configuration), or save one to the server.
- **Restore (import)** — upload a JSON backup to **replace** the whole database. The restore runs in
  a single transaction (a failure rolls back and leaves the DB untouched), a `pre-import` safety
  backup is written first, and the file is validated before anything is deleted (correct format,
  known tables, and at least one active ADMIN so you can't lock yourself out). It requires
  re-entering your password, and afterwards you're logged out to re-authenticate.
- **Automatic daily backups** — a background scheduler runs **once a day at a configurable time
  (default 22:05)** and writes an `auto-<timestamp>.json` backup only when the stored data actually
  changed since the last one (via a content fingerprint that ignores volatile fields such as
  API-client last-access and user last-login). The time of day, retention count and on/off are
  configurable on the page.

Backups are stored in `${pki-data}/backups/` with owner-only permissions where the OS supports it.
The import uses JSON only — it never executes SQL from the file.

### Online Documentation
A built-in **Documentation** menu (in the sidebar) with submenus links to in-app help pages at
`/pki-manager/docs/` — **Certificates**, **SCIM**, **API Clients** and **ACME**. Pages are rendered with
this deployment's own base URLs in the examples. Available to any signed-in user; unknown topics return 404.

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
# produces: target/macmario-service-PKIManager-1.4.war
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
cp target/macmario-service-PKIManager-1.4.war /var/lib/tomcat10/webapps/pki-manager.war
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

### Dashboard & Documentation
| URL | Description |
|---|---|
| `GET /pki-manager/dashboard` | Overview statistics |
| `GET /pki-manager/docs/` | Documentation home |
| `GET /pki-manager/docs/{certificates\|scim\|api-clients\|acme}` | Topic pages |

### Certificate Authorities
| URL | Description |
|---|---|
| `GET /pki-manager/ca` | CA list |
| `GET /pki-manager/ca/create` | New CA form |
| `POST /pki-manager/ca/create` | Create CA (generates key + cert immediately) |
| `GET /pki-manager/ca/import` | Import CA form |
| `POST /pki-manager/ca/import` | Import existing CA from PEM cert + private key (admin) |
| `GET /pki-manager/ca/{id}` | CA detail, child CAs, issued certificates |
| `GET /pki-manager/ca/{id}/cert.pem` | Download CA certificate as PEM |
| `POST /pki-manager/ca/{id}/enable` | Re-enable CA |
| `POST /pki-manager/ca/{id}/disable` | Disable CA |
| `POST /pki-manager/ca/{id}/revoke` | Revoke CA + descendants + issued certs (admin) |
| `POST /pki-manager/ca/{id}/delete` | Permanently delete CA + subtree, cascade (admin) |

### Certificates
| URL | Description |
|---|---|
| `GET /pki-manager/cert` | Certificate list |
| `GET /pki-manager/cert/issue` | Issue certificate form (generate or sign CSR) |
| `POST /pki-manager/cert/issue` | Issue certificate |
| `GET /pki-manager/cert/{id}` | Certificate detail + revoke form |
| `GET /pki-manager/cert/{id}/download.pem` | Download certificate PEM (authenticated) |
| `POST /pki-manager/cert/{id}/revoke` | Revoke certificate |

### API Clients (self-service, any signed-in user)
| URL | Description |
|---|---|
| `GET /pki-manager/api-clients/` | My API clients + request form |
| `POST /pki-manager/api-clients/request` | Request a new API client (PENDING) |
| `POST /pki-manager/api-clients/{id}/rotate` | Reveal / rotate my client's key (approved, own only) |
| `POST /pki-manager/api-clients/{id}/enable` | Enable my client |
| `POST /pki-manager/api-clients/{id}/disable` | Disable my client |
| `POST /pki-manager/api-clients/{id}/delete` | Delete / cancel my client |

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
| `GET /pki-manager/admin/backup/` | Backup & restore page |
| `GET /pki-manager/admin/backup/export` | Download a full JSON backup |
| `GET /pki-manager/admin/backup/download?name=` | Download a stored backup file |
| `POST /pki-manager/admin/backup/create` | Save a backup to the server |
| `POST /pki-manager/admin/backup/settings` | Update auto-backup settings |
| `POST /pki-manager/admin/backup/import` | Restore the database from an uploaded backup |
| `GET /pki-manager/admin/api-clients/` | API client list (all clients + pending requests) |
| `POST /pki-manager/admin/api-clients/{id}/approve` | Approve a pending API client request |
| `POST /pki-manager/admin/api-clients/{id}/reject` | Reject a pending API client request |
| `POST /pki-manager/admin/api-clients/create` | Create API client (generates key) |
| `GET /pki-manager/admin/api-clients/{id}/edit` | Edit API client form |
| `POST /pki-manager/admin/api-clients/{id}` | Update API client |
| `POST /pki-manager/admin/api-clients/{id}/enable` | Enable API client |
| `POST /pki-manager/admin/api-clients/{id}/disable` | Disable API client |
| `POST /pki-manager/admin/api-clients/{id}/rotate` | Rotate API key |
| `POST /pki-manager/admin/api-clients/{id}/delete` | Delete API client |

### SCIM 2.0 (bearer token, no session)
Base path `/pki-manager/scim/v2/` — `Authorization: Bearer <token>`.
| URL | Description |
|---|---|
| `GET/PUT/PATCH /Users`, `/Users/{id}` | Provision users (list/get/update/patch) |
| `GET/PUT/PATCH /Groups`, `/Groups/{ADMIN\|VIEWER}` | Role membership |
| `GET /Certificates`, `GET/PATCH /Certificates/{id}` | List/get; PATCH `status=REVOKED` to revoke |
| `GET/PUT/PATCH /ApiClients`, `/ApiClients/{id}` | Manage API clients (active/approval) |
| `GET /ServiceProviderConfig`, `/ResourceTypes`, `/Schemas` | Discovery |

### API (application access, no session required)
| URL | Description |
|---|---|
| `GET /pki-manager/api/v1/cas` | List active issuing CAs |
| `GET /pki-manager/api/v1/certs` | List own certificates |
| `POST /pki-manager/api/v1/certs` | Issue certificate (server generates key pair) |
| `POST /pki-manager/api/v1/certs/sign` | Auto-sign external CSR |
| `GET /pki-manager/api/v1/certs/{id}` | Certificate details (own only) |
| `GET /pki-manager/api/v1/certs/{id}/pem` | Download certificate PEM (own only) |
| `GET /pki-manager/api/v1/csr` | List own CSR jobs |
| `GET /pki-manager/api/v1/csr/{id}` | CSR job status (own only) |

---

## Architecture

```
src/main/java/com/macmario/services/pki/
├── filter/
│   ├── AppStartupListener.java   WebListener — DB init, default config and admin seed
│   ├── AuthFilter.java           WebFilter  — session check (reloads user live), redirects to /login
│   ├── ApiAuthFilter.java        WebFilter  — API key validation for /api/*
│   └── ScimAuthFilter.java       WebFilter  — SCIM bearer-token auth for /scim/*
├── entity/                       Plain Java beans (no JPA)
│   ├── CaConfig.java
│   ├── CertificateRecord.java
│   ├── RevokedCertificate.java
│   ├── CsrRequest.java
│   ├── AcmeCertificate.java
│   ├── ApiClient.java
│   └── PkiUser.java
├── service/
│   ├── CaService.java            CA CRUD, import, status, cascade revoke/delete
│   ├── CertificateService.java   Issue, revoke, query certificates (issuance guarded to ACTIVE CAs)
│   ├── PkiCryptoService.java     Bouncy Castle: key gen, CA init/import, Name Constraints, CSR signing
│   ├── CsrRequestService.java    CSR submission queue
│   ├── UserService.java          PBKDF2 auth, user CRUD
│   ├── ApiClientService.java     API client CRUD + key generation/rotation
│   ├── AcmeClientService.java    Full ACME v2 client (RFC 8555, ES256)
│   ├── ConfigService.java        Read/write PKI_CONFIGURATION settings
│   ├── BackupService.java        Full JSON export/import + change-triggered auto-backup
│   └── ScimService.java          SCIM 2.0 core logic (Users/Groups/Certificates/ApiClients)
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
│   ├── AcmeChallengeServlet.java
│   ├── ApiServlet.java           REST API for external applications (/api/v1/*)
│   ├── ApiClientAdminServlet.java  Admin UI for API client management + approvals
│   ├── MyApiClientServlet.java   User self-service API clients (/api-clients/*)
│   ├── BackupServlet.java        Admin backup & restore (/admin/backup/*)
│   ├── ScimServlet.java          SCIM 2.0 endpoint (/scim/v2/*)
│   └── DocsServlet.java          In-app online documentation (/docs/*)
└── util/
    └── EntityManagerProvider.java  Raw JDBC, H2 connection pool, schema creation
```

**Technology stack:**
- Jakarta Servlet 6.x — no Spring, no CDI
- Bouncy Castle 1.84 (`bcprov-jdk18on`, `bcpkix-jdk18on`) — all cryptography
- H2 2.4 embedded database — `AUTO_SERVER=TRUE`, persisted to disk
- Bootstrap 5.3 + Bootstrap Icons 1.11 — UI (CDN)
- Gson 2.11 — ACME JSON parsing + API request/response serialization
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
| `CA_CONFIG` | CA records: type, status, subject DN, PEM cert + private key, validity, `permitted_domains` (Name Constraints), revocation audit (`revoked_at`, `revocation_reason`, `revoked_by`) |
| `CERTIFICATE_RECORD` | Issued certificates: status, type, subject DN, SANs, PEM cert + optional private key, download token, `api_client_id` |
| `REVOKED_CERTIFICATE` | Revocation audit trail: reason, timestamp, operator |
| `PKI_CONFIGURATION` | Key-value runtime settings (`crl.validity.days`, `cert.expiry.warn.days`, etc.) |
| `PKI_USER` | User accounts: PBKDF2 password hash + salt, role, active flag |
| `CSR_REQUEST` | CSR submission queue: PEM, requester details, status, tracking token, `api_client_id` |
| `ACME_CERTIFICATE` | ACME-managed certificates: domain, account key, cert PEM, renewal status |
| `ACME_CHALLENGE_TOKEN` | Short-lived HTTP-01 challenge tokens served during ACME flow |
| `API_CLIENT` | API client records: name, API key, default CA, active flag, owner (`owner_user_id`) + approval (`approval_status`, `approved_by`), last access (`last_used_at`, `last_ip`, `last_user_agent`) |

Full-database JSON backups are written to `pki-data/backups/` (manual, `auto-*`, and `pre-import-*`).
Auto-backup behaviour is controlled by the `backup.auto.enabled`, `backup.auto.time` (HH:mm, daily,
default `22:05`), and `backup.auto.keep` keys in `PKI_CONFIGURATION` (editable on the Backup & Restore page).

---

## Security Notes

- **Change the default admin password** (`admin` / `admin`) immediately after first login.
- Private keys are stored **unencrypted** in the H2 database. Protect the database file
  (`$CATALINA_BASE/pki-data/`) with appropriate OS file permissions.
- **Backup files under `pki-data/backups/` contain the full database**, including unencrypted CA
  private keys, API keys and password hashes. They are written owner-only where POSIX permissions
  are supported; treat the whole `pki-data/` directory as a secret and protect exported files the
  same way.
- There is no rate limiting on the login or API endpoints. Place a reverse proxy (nginx, Apache)
  in front of Tomcat for production use.
- API keys are stored in plain text in the H2 database (same threat model as private keys).
  Treat the `pki-data/` directory as a secret. Rotate keys immediately if they are exposed.
- API clients are strictly scoped: they cannot access CA management or other clients' certificates.
  Enforce network-level restrictions so the API port is not publicly reachable if not required.
- The **SCIM bearer token** grants full identity management (including promotion to ADMIN). Only its
  SHA-256 is stored; treat the token like an admin credential, rotate it on the Users admin page if exposed,
  and expose `/scim/*` only to trusted provisioning systems (ideally over TLS and network-restricted).
- State-changing API-client actions (self-service and admin approve/reject) enforce a same-origin check
  on `Origin`/`Referer`. Behind a reverse proxy the proxy **must preserve the `Host` header**
  (`ProxyPreserveHost On` in Apache; nginx `proxy_set_header Host $host;`), or these actions will be rejected.
- The ACME flow requires the server to be reachable from the internet on port 80 for HTTP-01
  challenges. Use staging (`Let's Encrypt Staging`) to test without hitting rate limits.
- Session timeout is 60 minutes (configurable in `LoginServlet`).
