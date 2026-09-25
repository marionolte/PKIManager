<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.ApiClient, com.macmario.services.pki.entity.CaConfig, com.macmario.services.pki.entity.PkiUser" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");} %>
<% List<ApiClient> clients=(List<ApiClient>)request.getAttribute("clients");
   if(clients==null)clients=java.util.Collections.emptyList();
   List<CaConfig> allCas=(List<CaConfig>)request.getAttribute("allCas");
   if(allCas==null)allCas=java.util.Collections.emptyList();
   String newApiKey=(String)request.getAttribute("newApiKey");
   String flashMsg=(String)request.getAttribute("flashMsg");
   String error=(String)request.getAttribute("error");
   String ctx=request.getContextPath();
   PkiUser me=(PkiUser)session.getAttribute("currentUser");
   boolean isAdmin=me!=null&&me.isAdmin();
   Long pendingCount=(Long)request.getAttribute("pendingCount");
   String scheme=request.getScheme(); int port=request.getServerPort();
   String portPart=(("http".equals(scheme)&&port==80)||("https".equals(scheme)&&port==443))?"":(":"+port);
   String base=scheme+"://"+request.getServerName()+portPart+ctx;
%>
<!DOCTYPE html><html lang="en">
<head><meta charset="UTF-8"/><title>PKI Manager – My API Clients</title>
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
.table-card .table{margin:0;} .table-card .table thead{background:#dde4ee;color:#334155;font-size:.78rem;text-transform:uppercase;}
.table-card .table thead th{border:none;padding:.9rem 1rem;font-weight:500;}
.table-card .table tbody td{padding:.75rem 1rem;vertical-align:middle;font-size:.875rem;border-color:#f0f0f0;}
.key-box{font-family:monospace;font-size:.75rem;background:#f1f5f9;border:1px solid #dde4ee;border-radius:6px;padding:.35rem .6rem;word-break:break-all;}
.curl-pre{background:#0f172a;color:#e2e8f0;border-radius:8px;padding:.9rem 1rem;font-size:.75rem;line-height:1.5;overflow-x:auto;margin:0;}
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
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
    <div class="nav-sect mt-2">Integration</div>
    <a href="<%=ctx%>/api-clients/" class="nav-link active"><i class="bi bi-key"></i>My API Clients</a>
<% if(isAdmin){ %>
    <div class="nav-sect mt-2">Administration</div>
    <a href="<%=ctx%>/admin/users/" class="nav-link"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
    <a href="<%=ctx%>/admin/api-clients" class="nav-link"><i class="bi bi-key-fill"></i>API Clients (admin)</a>
    <a href="<%=ctx%>/admin/backup/" class="nav-link"><i class="bi bi-hdd-stack"></i>Backup &amp; Restore</a>
<% } %>
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
    <span style="font-weight:600;color:#0d1b2a;"><i class="bi bi-key me-2"></i>My API Clients</span>
    <div class="d-flex gap-2">
<% if(isAdmin){ %>
      <a href="<%=ctx%>/admin/api-clients" class="btn btn-outline-primary btn-sm">
        <i class="bi bi-clipboard-check me-1"></i>Approvals<% if(pendingCount!=null&&pendingCount>0){ %> <span class="badge bg-danger"><%=pendingCount%></span><% } %>
      </a>
<% } %>
      <button class="btn btn-success btn-sm" data-bs-toggle="modal" data-bs-target="#requestModal"><i class="bi bi-plus me-1"></i>Request API Client</button>
    </div>
  </div>
  <div class="content-area">

    <% if(newApiKey!=null){ %>
    <div class="alert alert-success alert-dismissible mb-4">
      <i class="bi bi-check-circle me-2"></i><strong>API Key</strong> — copy it now, it will not be shown again in full:
      <div class="d-flex align-items-center gap-2 mt-2">
        <span class="key-box flex-grow-1" id="newKey"><%=e(newApiKey)%></span>
        <button class="btn btn-sm btn-outline-success" onclick="navigator.clipboard.writeText(document.getElementById('newKey').textContent);this.textContent='Copied!'">Copy</button>
      </div>
      <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    <% } %>
    <% if(flashMsg!=null){ %><div class="alert alert-info"><i class="bi bi-info-circle me-2"></i><%=e(flashMsg)%></div><% } %>
    <% if(error!=null){ %><div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>

    <% if(clients.isEmpty()){ %>
    <div class="text-center py-5">
      <i class="bi bi-key fs-1 text-muted d-block mb-3 opacity-25"></i>
      <h5 class="text-muted">You have no API clients yet</h5>
      <p class="text-muted" style="font-size:.85rem;">Request one below — an administrator must approve it before the key works.</p>
      <button class="btn btn-success mt-1" data-bs-toggle="modal" data-bs-target="#requestModal"><i class="bi bi-plus-circle me-1"></i>Request API Client</button>
    </div>
    <% } else { %>
    <div class="table-card">
      <table class="table table-hover">
        <thead><tr><th>Name</th><th>Description</th><th>Default CA</th><th>Status</th><th>API Key</th><th></th></tr></thead>
        <tbody>
        <% for(ApiClient ac : clients){
             String stBadge; String stLabel;
             if(ac.isPending()){ stBadge="bg-warning text-dark"; stLabel="Pending approval"; }
             else if(ac.isRejected()){ stBadge="bg-danger"; stLabel="Rejected"; }
             else if(ac.isActive()){ stBadge="bg-success"; stLabel="Active"; }
             else { stBadge="bg-secondary"; stLabel="Disabled"; } %>
        <tr>
          <td class="fw-semibold"><%=e(ac.getName())%></td>
          <td style="font-size:.82rem;color:#64748b;"><%=e(ac.getDescription())%></td>
          <td style="font-size:.82rem;"><%=ac.getDefaultCaName()!=null?e(ac.getDefaultCaName()):"—"%></td>
          <td><span class="badge <%=stBadge%>"><%=stLabel%></span></td>
          <td>
            <% if(ac.isApproved()){ %><span class="key-box"><%=e(ac.getApiKey().substring(0,Math.min(12,ac.getApiKey().length())))%>…</span><% } else { %><span class="text-muted">—</span><% } %>
          </td>
          <td>
            <div class="d-flex gap-1 flex-wrap justify-content-end">
              <% if(ac.isApproved()){ %>
              <form method="post" action="<%=ctx%>/api-clients/<%=ac.getId()%>/rotate" class="d-inline"
                    data-name="<%=e(ac.getName())%>" onsubmit="return confirm('Generate a new key for ' + this.dataset.name + '? The old key stops working.')">
                <button class="btn btn-sm btn-outline-secondary py-0 px-2" title="Reveal / rotate key"><i class="bi bi-arrow-repeat"></i> Key</button>
              </form>
                <% if(ac.isActive()){ %>
                <form method="post" action="<%=ctx%>/api-clients/<%=ac.getId()%>/disable" class="d-inline">
                  <button class="btn btn-sm btn-outline-warning py-0 px-2" title="Disable"><i class="bi bi-pause-circle"></i></button>
                </form>
                <% } else { %>
                <form method="post" action="<%=ctx%>/api-clients/<%=ac.getId()%>/enable" class="d-inline">
                  <button class="btn btn-sm btn-outline-success py-0 px-2" title="Enable"><i class="bi bi-play-circle"></i></button>
                </form>
                <% } %>
              <% } %>
              <form method="post" action="<%=ctx%>/api-clients/<%=ac.getId()%>/delete" class="d-inline"
                    data-name="<%=e(ac.getName())%>" onsubmit="return confirm('Delete ' + this.dataset.name + '?')">
                <button class="btn btn-sm btn-outline-danger py-0 px-2" title="<%=ac.isPending()?"Cancel request":"Delete"%>"><i class="bi bi-trash"></i></button>
              </form>
            </div>
          </td>
        </tr>
        <% } %>
        </tbody>
      </table>
    </div>
    <% } %>

    <div class="mt-4 p-4" style="background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);">
      <h6 class="fw-bold mb-2"><i class="bi bi-terminal me-2 text-success"></i>Using your API key</h6>
      <p style="font-size:.82rem;color:#555;">Send it in the <code>X-API-Key</code> header. Base URL: <code><%=e(base)%></code></p>
      <pre class="curl-pre"><code>curl -H "X-API-Key: pki_YOUR_API_KEY" <%=e(base)%>/api/v1/certs</code></pre>
      <p class="mb-0 mt-2" style="font-size:.78rem;color:#888;">A client works only after an administrator approves it. You can only see and manage your own clients.</p>
    </div>
  </div>
</div>

<div class="modal fade" id="requestModal" tabindex="-1">
  <div class="modal-dialog"><div class="modal-content">
    <div class="modal-header">
      <h5 class="modal-title"><i class="bi bi-plus-circle me-2 text-success"></i>Request API Client</h5>
      <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
    </div>
    <form method="post" action="<%=ctx%>/api-clients/request">
      <div class="modal-body">
        <% if(!isAdmin){ %>
        <div class="alert alert-info py-2" style="font-size:.82rem;"><i class="bi bi-info-circle me-1"></i>Your request must be approved by an administrator before the key can be used.</div>
        <% } %>
        <div class="mb-3">
          <label class="form-label fw-semibold">Name <span class="text-danger">*</span></label>
          <input name="name" class="form-control" required maxlength="100" placeholder="e.g. MyApp-Backend"/>
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold">Description</label>
          <textarea name="description" rows="2" class="form-control" placeholder="What will this client do?"></textarea>
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold">Preferred Issuing CA</label>
          <select name="defaultCaId" class="form-select">
            <option value="">— none (specify caId per request) —</option>
            <% for(CaConfig ca : allCas){ if(ca.getStatus()==CaConfig.CaStatus.ACTIVE){ %>
            <option value="<%=ca.getId()%>"><%=e(ca.getDisplayName())%> (<%=ca.getCaType()%>)</option>
            <% } } %>
          </select>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
        <button type="submit" class="btn btn-success"><i class="bi bi-send me-1"></i><%=isAdmin?"Create":"Submit Request"%></button>
      </div>
    </form>
  </div></div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
