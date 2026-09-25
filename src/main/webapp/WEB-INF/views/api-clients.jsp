<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.ApiClient, com.macmario.services.pki.entity.CaConfig, com.macmario.services.pki.entity.PkiUser" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private String fmt(java.time.LocalDateTime t){return t==null?"":t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));} %>
<% List<ApiClient> clients=(List<ApiClient>)request.getAttribute("clients");
   if(clients==null)clients=java.util.Collections.emptyList();
   List<CaConfig> allCas=(List<CaConfig>)request.getAttribute("allCas");
   if(allCas==null)allCas=java.util.Collections.emptyList();
   ApiClient editClient=(ApiClient)request.getAttribute("client");
   String newApiKey=(String)request.getAttribute("newApiKey");
   Long newApiKeyClientId=(Long)request.getAttribute("newApiKeyClientId");
   String error=(String)request.getAttribute("error");
   String ctx=request.getContextPath();
   PkiUser me=(PkiUser)session.getAttribute("currentUser");
   String scheme=request.getScheme();
   int port=request.getServerPort();
   String portPart=(("http".equals(scheme)&&port==80)||("https".equals(scheme)&&port==443))?"":(":"+port);
   String base=scheme+"://"+request.getServerName()+portPart+ctx;
%>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – API Clients</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-dark:#e8ecf0;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;}
.sidebar{position:fixed;top:0;left:0;width:240px;height:100vh;background:var(--pki-dark);border-right:1px solid #d1d9e0;display:flex;flex-direction:column;z-index:100;}
.sidebar-brand{padding:1.5rem 1.2rem;border-bottom:1px solid rgba(0,0,0,.08);}
.sidebar-brand h5{color:#1b4f8a;font-weight:700;margin:0;font-size:.95rem;}
.nav-sect{padding:.5rem 1rem .2rem;font-size:.68rem;text-transform:uppercase;letter-spacing:.08em;color:#64748b;}
.sidebar .nav-link{color:#334155;padding:.5rem 1.2rem;font-size:.875rem;border-radius:0;}
.sidebar .nav-link:hover,.sidebar .nav-link.active{background:rgba(27,79,138,.1);color:#1b4f8a;}
.sidebar .nav-link i{width:20px;margin-right:8px;}
.main-content{margin-left:240px;min-height:100vh;}
.topbar{background:#fff;border-bottom:1px solid #dde4ee;padding:.75rem 2rem;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:50;}
.content-area{padding:2rem;}
.table-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);overflow:hidden;}
.table-card .table{margin:0;}
.table-card .table thead{background:#dde4ee;color:#334155;font-size:.78rem;text-transform:uppercase;}
.table-card .table thead th{border:none;padding:.9rem 1rem;font-weight:500;}
.table-card .table tbody td{padding:.75rem 1rem;vertical-align:middle;font-size:.875rem;border-color:#f0f0f0;}
.table-card .table tbody tr:hover{background:#f7faff;}
.key-box{font-family:monospace;font-size:.75rem;background:#f1f5f9;border:1px solid #dde4ee;border-radius:6px;padding:.35rem .6rem;word-break:break-all;}
.curl-block{position:relative;margin-bottom:1rem;}
.curl-label{font-size:.75rem;font-weight:600;color:#475569;margin-bottom:.25rem;}
.curl-pre{background:#0f172a;color:#e2e8f0;border-radius:8px;padding:.9rem 1rem;font-size:.75rem;line-height:1.5;overflow-x:auto;margin:0;}
.curl-pre code{font-family:'Courier New',monospace;white-space:pre;}
.curl-copy{position:absolute;top:1.8rem;right:.5rem;font-size:.7rem;padding:.15rem .5rem;background:#1e293b;color:#cbd5e1;border:1px solid #334155;border-radius:6px;cursor:pointer;}
.curl-copy:hover{background:#334155;color:#fff;}
.access-row{display:flex;gap:.5rem;padding:.5rem 0;border-bottom:1px solid #f1f5f9;font-size:.85rem;}
.access-label{min-width:120px;color:#64748b;font-weight:500;}
</style></head>
<body>
<div class="sidebar">
  <div class="sidebar-brand"><div class="d-flex align-items-center gap-2 mb-1">
    <i class="bi bi-shield-lock-fill fs-4" style="color:var(--pki-teal)"></i><h5>PKI Manager</h5>
  </div><small style="color:#888;font-size:.72rem;">MHService Internal CA</small></div>
  <nav class="flex-grow-1 py-2">
    <div class="nav-sect">Overview</div>
    <a href="<%=ctx%>/dashboard" class="nav-link"><i class="bi bi-speedometer2"></i>Dashboard</a>
    <div class="nav-sect mt-2">PKI Hierarchy</div>
    <a href="<%=ctx%>/ca" class="nav-link"><i class="bi bi-diagram-3"></i>Certificate Authorities</a>
    <a href="<%=ctx%>/ca/create" class="nav-link"><i class="bi bi-plus-circle"></i>New CA</a>
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
    <div class="nav-sect mt-2">Requests</div>
    <a href="<%=ctx%>/admin/csr-jobs" class="nav-link"><i class="bi bi-inbox"></i>CSR Jobs</a>
    <div class="nav-sect mt-2">Administration</div>
    <a href="<%=ctx%>/admin/users/" class="nav-link"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
    <a href="<%=ctx%>/admin/api-clients" class="nav-link active"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/admin/backup/" class="nav-link"><i class="bi bi-hdd-stack"></i>Backup &amp; Restore</a>
      <div class="nav-sect mt-2"><i class="bi bi-book me-1"></i>Documentation</div>
    <a href="<%=ctx%>/docs/certificates" class="nav-link"><i class="bi bi-file-earmark-text"></i>Certificates</a>
    <a href="<%=ctx%>/docs/scim" class="nav-link"><i class="bi bi-people"></i>SCIM</a>
    <a href="<%=ctx%>/docs/api-clients" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/docs/acme" class="nav-link"><i class="bi bi-lock"></i>ACME</a>
</nav>
  <div class="p-3" style="border-top:1px solid rgba(0,0,0,.08);font-size:.72rem;color:#64748b;">
    <i class="bi bi-person-circle me-1"></i><%=me!=null?e(me.getDisplayName()):""%>
    <form method="post" action="<%=ctx%>/logout" class="d-inline ms-2">
      <button class="btn btn-link btn-sm p-0 text-danger" style="font-size:.72rem;"><i class="bi bi-box-arrow-right"></i> Logout</button>
    </form>
  </div>
</div>
<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:#0d1b2a;"><i class="bi bi-key me-2"></i>API Clients</span>
    <button class="btn btn-success btn-sm" data-bs-toggle="modal" data-bs-target="#createModal">
      <i class="bi bi-plus me-1"></i>New API Client
    </button>
  </div>
  <div class="content-area">

    <%-- Flash: new / rotated API key --%>
    <% if(newApiKey!=null){ %>
    <div class="alert alert-success alert-dismissible mb-4" role="alert">
      <i class="bi bi-check-circle me-2"></i><strong>API Key</strong> — copy it now, it will not be shown again in full:
      <div class="d-flex align-items-center gap-2 mt-2">
        <span class="key-box flex-grow-1" id="newKey"><%=e(newApiKey)%></span>
        <button class="btn btn-sm btn-outline-success" onclick="navigator.clipboard.writeText(document.getElementById('newKey').textContent);this.textContent='Copied!'">Copy</button>
      </div>
      <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    <% } %>

    <% if(error!=null){ %><div class="alert alert-danger mb-4"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>

    <% if(clients.isEmpty()){ %>
    <div class="text-center py-5">
      <i class="bi bi-key fs-1 text-muted d-block mb-3 opacity-25"></i>
      <h5 class="text-muted">No API clients yet</h5>
      <button class="btn btn-success mt-2" data-bs-toggle="modal" data-bs-target="#createModal">
        <i class="bi bi-plus-circle me-1"></i>Create First API Client
      </button>
    </div>
    <% } else { %>
    <div class="table-card">
      <table class="table table-hover">
        <thead><tr>
          <th>Name</th><th>Owner</th><th>Default CA</th>
          <th>API Key (masked)</th><th>Status</th><th></th>
        </tr></thead>
        <tbody>
        <% for(ApiClient ac : clients){
             String stBadge; String stLabel;
             if(ac.isPending()){ stBadge="bg-warning text-dark"; stLabel="Pending"; }
             else if(ac.isRejected()){ stBadge="bg-danger"; stLabel="Rejected"; }
             else if(ac.isActive()){ stBadge="bg-success"; stLabel="Active"; }
             else { stBadge="bg-secondary"; stLabel="Disabled"; } %>
        <tr>
          <td class="fw-semibold"><%=e(ac.getName())%>
            <% if(ac.getDescription()!=null&&!ac.getDescription().isBlank()){ %><div style="font-size:.75rem;color:#94a3b8;"><%=e(ac.getDescription())%></div><% } %>
          </td>
          <td style="font-size:.82rem;"><%=ac.getOwnerName()!=null?e(ac.getOwnerName()):"<span class='text-muted'>—</span>"%></td>
          <td style="font-size:.82rem;"><%=ac.getDefaultCaName()!=null?e(ac.getDefaultCaName()):"<span class='text-muted'>—</span>"%></td>
          <td>
            <% if(ac.isApproved()){ %><span class="key-box"><%=e(ac.getApiKey().substring(0,Math.min(12,ac.getApiKey().length())))%>…</span><% } else { %><span class="text-muted">—</span><% } %>
          </td>
          <td><span class="badge <%=stBadge%>"><%=stLabel%></span></td>
          <td>
            <div class="d-flex gap-1 flex-wrap justify-content-end">
              <% if(ac.isPending() || ac.isRejected()){ %>
              <%-- Pending or revoked: approve/reinstate (and reject while pending) --%>
              <form method="post" action="<%=ctx%>/admin/api-clients/<%=ac.getId()%>/approve" class="d-inline"
                    data-name="<%=e(ac.getName())%>" onsubmit="return confirm('<%=ac.isRejected()?"Reinstate":"Approve"%> API client ' + this.dataset.name + '?')">
                <button class="btn btn-sm btn-success py-0 px-2" title="<%=ac.isRejected()?"Reinstate":"Approve"%>"><i class="bi bi-check-lg"></i> <%=ac.isRejected()?"Reinstate":"Approve"%></button>
              </form>
              <% if(ac.isPending()){ %>
              <form method="post" action="<%=ctx%>/admin/api-clients/<%=ac.getId()%>/reject" class="d-inline"
                    data-name="<%=e(ac.getName())%>" onsubmit="return confirm('Reject API client ' + this.dataset.name + '?')">
                <button class="btn btn-sm btn-outline-danger py-0 px-2" title="Reject"><i class="bi bi-x-lg"></i></button>
              </form>
              <% } %>
              <% } else { %>
              <%-- Access info --%>
              <button class="btn btn-sm btn-outline-info py-0 px-2" title="Access info"
                data-bs-toggle="modal" data-bs-target="#accessModal"
                data-name="<%=e(ac.getName())%>"
                data-lastused="<%=fmt(ac.getLastUsedAt())%>"
                data-ip="<%=e(ac.getLastIp()!=null?ac.getLastIp():"")%>"
                data-ua="<%=e(ac.getLastUserAgent()!=null?ac.getLastUserAgent():"")%>">
                <i class="bi bi-activity"></i>
              </button>
              <%-- Edit --%>
              <button class="btn btn-sm btn-outline-primary py-0 px-2"
                data-bs-toggle="modal" data-bs-target="#editModal"
                data-id="<%=ac.getId()%>"
                data-name="<%=e(ac.getName())%>"
                data-desc="<%=e(ac.getDescription()!=null?ac.getDescription():"")%>"
                data-caid="<%=ac.getDefaultCaId()!=null?ac.getDefaultCaId():""%>">
                <i class="bi bi-pencil"></i>
              </button>
              <%-- Enable / Disable (owned clients are revoked so the owner can't re-enable) --%>
              <% if(ac.isActive() && ac.getOwnerUserId()!=null){ %>
              <form method="post" action="<%=ctx%>/admin/api-clients/<%=ac.getId()%>/reject" class="d-inline"
                    data-name="<%=e(ac.getName())%>" onsubmit="return confirm('Revoke API client ' + this.dataset.name + '? The owner cannot re-enable it.')">
                <button class="btn btn-sm btn-outline-warning py-0 px-2" title="Revoke"><i class="bi bi-slash-circle"></i></button>
              </form>
              <% } else if(ac.isActive()){ %>
              <form method="post" action="<%=ctx%>/admin/api-clients/<%=ac.getId()%>/disable" class="d-inline">
                <button class="btn btn-sm btn-outline-warning py-0 px-2" title="Disable"><i class="bi bi-pause-circle"></i></button>
              </form>
              <% } else { %>
              <form method="post" action="<%=ctx%>/admin/api-clients/<%=ac.getId()%>/enable" class="d-inline">
                <button class="btn btn-sm btn-outline-success py-0 px-2" title="Enable"><i class="bi bi-play-circle"></i></button>
              </form>
              <% } %>
              <%-- Rotate key --%>
              <form method="post" action="<%=ctx%>/admin/api-clients/<%=ac.getId()%>/rotate" class="d-inline"
                    data-name="<%=e(ac.getName())%>" onsubmit="return confirm('Rotate API key for ' + this.dataset.name + '? The old key stops working immediately.')">
                <button class="btn btn-sm btn-outline-secondary py-0 px-2" title="Rotate API key"><i class="bi bi-arrow-repeat"></i></button>
              </form>
              <% } %>
              <%-- Delete (always available) --%>
              <form method="post" action="<%=ctx%>/admin/api-clients/<%=ac.getId()%>/delete" class="d-inline"
                    data-name="<%=e(ac.getName())%>" onsubmit="return confirm('Delete API client ' + this.dataset.name + '? Issued certificates remain but the key stops working.')">
                <button class="btn btn-sm btn-outline-danger py-0 px-2" title="Delete"><i class="bi bi-trash"></i></button>
              </form>
            </div>
          </td>
        </tr>
        <% } %>
        </tbody>
      </table>
    </div>
    <% } %>

    <%-- API Reference --%>
    <div class="mt-4 p-4" style="background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);">
      <h6 class="fw-bold mb-3"><i class="bi bi-code-slash me-2 text-primary"></i>API Reference</h6>
      <p style="font-size:.82rem;color:#555;margin-bottom:.75rem;">Authenticate with <code>X-API-Key: &lt;key&gt;</code> header. Base path: <code><%=ctx%>/api/v1/</code></p>
      <table class="table table-sm" style="font-size:.8rem;">
        <thead><tr><th style="width:40px;">Method</th><th>Endpoint</th><th>Description</th></tr></thead>
        <tbody>
          <tr><td><span class="badge bg-primary">GET</span></td><td><code>/api/v1/cas</code></td><td>List available issuing CAs</td></tr>
          <tr><td><span class="badge bg-primary">GET</span></td><td><code>/api/v1/certs</code></td><td>List own certificates</td></tr>
          <tr><td><span class="badge bg-success">POST</span></td><td><code>/api/v1/certs</code></td><td>Issue certificate (server generates key pair)</td></tr>
          <tr><td><span class="badge bg-success">POST</span></td><td><code>/api/v1/certs/sign</code></td><td>Auto-sign external CSR, returns certificate immediately</td></tr>
          <tr><td><span class="badge bg-primary">GET</span></td><td><code>/api/v1/certs/{id}</code></td><td>Get certificate details (own only)</td></tr>
          <tr><td><span class="badge bg-primary">GET</span></td><td><code>/api/v1/certs/{id}/pem</code></td><td>Download certificate PEM (own only)</td></tr>
          <tr><td><span class="badge bg-primary">GET</span></td><td><code>/api/v1/csr</code></td><td>List own CSR jobs</td></tr>
          <tr><td><span class="badge bg-primary">GET</span></td><td><code>/api/v1/csr/{id}</code></td><td>Get CSR job status (own only)</td></tr>
        </tbody>
      </table>
      <p class="mb-0 mt-2" style="font-size:.78rem;color:#888;">API clients cannot access CA management. Each client only sees its own certificates.</p>

      <h6 class="fw-bold mt-4 mb-2"><i class="bi bi-terminal me-2 text-success"></i>curl Examples</h6>
      <p style="font-size:.8rem;color:#555;">Replace <code>pki_YOUR_API_KEY</code> with your client key. Base URL is auto-detected: <code><%=e(base)%></code></p>

      <div class="curl-block">
        <div class="curl-label">List available issuing CAs</div>
        <pre class="curl-pre"><code>curl -H "X-API-Key: pki_YOUR_API_KEY" \
  <%=e(base)%>/api/v1/cas</code></pre>
      </div>

      <div class="curl-block">
        <div class="curl-label">List your own certificates</div>
        <pre class="curl-pre"><code>curl -H "X-API-Key: pki_YOUR_API_KEY" \
  <%=e(base)%>/api/v1/certs</code></pre>
      </div>

      <div class="curl-block">
        <div class="curl-label">Issue a certificate (server generates the key pair)</div>
        <pre class="curl-pre"><code>curl -X POST \
  -H "X-API-Key: pki_YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "commonName": "app.example.local",
    "certType": "SERVER",
    "sanDns": "app.example.local,www.example.local",
    "organization": "ACME Corp",
    "keySize": 2048,
    "caId": 1
  }' \
  <%=e(base)%>/api/v1/certs</code></pre>
      </div>

      <div class="curl-block">
        <div class="curl-label">Sign an external CSR (returns the certificate immediately)</div>
        <pre class="curl-pre"><code># Turn a PEM CSR into a JSON string and post it:
CSR=$(awk '{printf "%s\\n", $0}' request.csr)
curl -X POST \
  -H "X-API-Key: pki_YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"csrPem\": \"$CSR\", \"certType\": \"SERVER\", \"caId\": 1}" \
  <%=e(base)%>/api/v1/certs/sign</code></pre>
      </div>

      <div class="curl-block">
        <div class="curl-label">Get certificate details / download PEM (own certificates only)</div>
        <pre class="curl-pre"><code>curl -H "X-API-Key: pki_YOUR_API_KEY" <%=e(base)%>/api/v1/certs/123
curl -H "X-API-Key: pki_YOUR_API_KEY" <%=e(base)%>/api/v1/certs/123/pem -o cert.pem</code></pre>
      </div>

      <div class="curl-block">
        <div class="curl-label">List / check CSR jobs</div>
        <pre class="curl-pre"><code>curl -H "X-API-Key: pki_YOUR_API_KEY" <%=e(base)%>/api/v1/csr
curl -H "X-API-Key: pki_YOUR_API_KEY" <%=e(base)%>/api/v1/csr/45</code></pre>
      </div>
    </div>
  </div>
</div>

<%-- Create Modal --%>
<div class="modal fade" id="createModal" tabindex="-1">
  <div class="modal-dialog">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title"><i class="bi bi-plus-circle me-2 text-success"></i>New API Client</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
      </div>
      <form method="post" action="<%=ctx%>/admin/api-clients/create">
        <div class="modal-body">
          <div class="mb-3">
            <label class="form-label fw-semibold">Name <span class="text-danger">*</span></label>
            <input name="name" class="form-control" required placeholder="e.g. MyApp-Backend"/>
          </div>
          <div class="mb-3">
            <label class="form-label fw-semibold">Description</label>
            <textarea name="description" rows="2" class="form-control" placeholder="What does this client do?"></textarea>
          </div>
          <div class="mb-3">
            <label class="form-label fw-semibold">Default Issuing CA</label>
            <select name="defaultCaId" class="form-select">
              <option value="">— none (must specify caId per request) —</option>
              <% for(CaConfig ca : allCas){ if(ca.getStatus()==CaConfig.CaStatus.ACTIVE){ %>
              <option value="<%=ca.getId()%>"><%=e(ca.getDisplayName())%> (<%=ca.getCaType()%>)</option>
              <% } } %>
            </select>
            <div class="form-text">If set, the client can omit <code>caId</code> in API requests.</div>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="submit" class="btn btn-success"><i class="bi bi-plus-circle me-1"></i>Create</button>
        </div>
      </form>
    </div>
  </div>
</div>

<%-- Access Info Modal --%>
<div class="modal fade" id="accessModal" tabindex="-1">
  <div class="modal-dialog">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title"><i class="bi bi-activity me-2 text-info"></i>API Client Access</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
      </div>
      <div class="modal-body">
        <div class="access-row"><span class="access-label">Client</span><span id="accName" class="fw-semibold"></span></div>
        <div class="access-row"><span class="access-label">Last Login</span><span id="accLastUsed"></span></div>
        <div class="access-row"><span class="access-label">Remote IP</span><code id="accIp"></code></div>
        <div class="access-row" style="border-bottom:none;"><span class="access-label">User-Agent</span><span id="accUa" style="word-break:break-all;"></span></div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
      </div>
    </div>
  </div>
</div>

<%-- Edit Modal --%>
<div class="modal fade" id="editModal" tabindex="-1">
  <div class="modal-dialog">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title"><i class="bi bi-pencil me-2 text-primary"></i>Edit API Client</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
      </div>
      <form method="post" action="" id="editForm">
        <div class="modal-body">
          <div class="mb-3">
            <label class="form-label fw-semibold">Name <span class="text-danger">*</span></label>
            <input name="name" id="editName" class="form-control" required/>
          </div>
          <div class="mb-3">
            <label class="form-label fw-semibold">Description</label>
            <textarea name="description" id="editDesc" rows="2" class="form-control"></textarea>
          </div>
          <div class="mb-3">
            <label class="form-label fw-semibold">Default Issuing CA</label>
            <select name="defaultCaId" id="editCaId" class="form-select">
              <option value="">— none —</option>
              <% for(CaConfig ca : allCas){ if(ca.getStatus()==CaConfig.CaStatus.ACTIVE){ %>
              <option value="<%=ca.getId()%>"><%=e(ca.getDisplayName())%> (<%=ca.getCaType()%>)</option>
              <% } } %>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="submit" class="btn btn-primary"><i class="bi bi-save me-1"></i>Save</button>
        </div>
      </form>
    </div>
  </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script>
document.getElementById('editModal').addEventListener('show.bs.modal', function(e) {
  var btn = e.relatedTarget;
  document.getElementById('editName').value = btn.dataset.name;
  document.getElementById('editDesc').value = btn.dataset.desc;
  var caId = btn.dataset.caid;
  var sel = document.getElementById('editCaId');
  for(var i=0;i<sel.options.length;i++) sel.options[i].selected = (sel.options[i].value === caId);
  document.getElementById('editForm').action = '<%=ctx%>/admin/api-clients/' + btn.dataset.id;
});

document.getElementById('accessModal').addEventListener('show.bs.modal', function(e) {
  var btn = e.relatedTarget;
  document.getElementById('accName').textContent = btn.dataset.name || '';
  document.getElementById('accLastUsed').textContent = btn.dataset.lastused || 'Never used';
  document.getElementById('accIp').textContent = btn.dataset.ip || '—';
  document.getElementById('accUa').textContent = btn.dataset.ua || '—';
});

// Add a Copy button to each curl example
document.querySelectorAll('.curl-block').forEach(function(block){
  var btn = document.createElement('button');
  btn.className = 'curl-copy';
  btn.type = 'button';
  btn.textContent = 'Copy';
  btn.addEventListener('click', function(){
    var code = block.querySelector('code').textContent;
    navigator.clipboard.writeText(code).then(function(){
      btn.textContent = 'Copied!';
      setTimeout(function(){ btn.textContent = 'Copy'; }, 1500);
    });
  });
  block.appendChild(btn);
});
</script>
</body></html>
