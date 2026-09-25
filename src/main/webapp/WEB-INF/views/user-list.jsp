<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.PkiUser" %>
<%
    List<PkiUser> users = (List<PkiUser>) request.getAttribute("users");
    String ctx   = request.getContextPath();
    String error = (String) request.getAttribute("error");
    PkiUser me   = (PkiUser) session.getAttribute("currentUser");
    if (users == null) users = java.util.Collections.emptyList();
    boolean scimConfigured = Boolean.TRUE.equals(request.getAttribute("scimConfigured"));
    String newScimToken = (String) request.getAttribute("newScimToken");
    String scimBase = request.getScheme()+"://"+request.getServerName()
        + (((request.getScheme().equals("http")&&request.getServerPort()==80)||(request.getScheme().equals("https")&&request.getServerPort()==443))?"":(":"+request.getServerPort()))
        + ctx + "/scim/v2";
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – Users</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-dark:#e8ecf0;--pki-blue:#1b4f8a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
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
</style>
</head>
<body>
<div class="sidebar">
  <div class="sidebar-brand">
    <div class="d-flex align-items-center gap-2 mb-1">
      <i class="bi bi-shield-lock-fill fs-4" style="color:var(--pki-teal)"></i><h5>PKI Manager</h5>
    </div>
    <small style="color:#888;font-size:.72rem;">MHService Internal CA</small>
  </div>
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
    <a href="<%=ctx%>/admin/users/" class="nav-link active"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
    <a href="<%=ctx%>/api-clients/" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/admin/backup/" class="nav-link"><i class="bi bi-hdd-stack"></i>Backup &amp; Restore</a>
      <div class="nav-sect mt-2"><i class="bi bi-book me-1"></i>Documentation</div>
    <a href="<%=ctx%>/docs/certificates" class="nav-link"><i class="bi bi-file-earmark-text"></i>Certificates</a>
    <a href="<%=ctx%>/docs/scim" class="nav-link"><i class="bi bi-people"></i>SCIM</a>
    <a href="<%=ctx%>/docs/api-clients" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/docs/acme" class="nav-link"><i class="bi bi-lock"></i>ACME</a>
</nav>
  <div class="p-3" style="border-top:1px solid rgba(0,0,0,.08);font-size:.72rem;color:#64748b;">
    <i class="bi bi-person-circle me-1"></i><%=me != null ? me.getDisplayName() : ""%>
    <form method="post" action="<%=ctx%>/logout" class="d-inline ms-2">
      <button class="btn btn-link btn-sm p-0 text-danger" style="font-size:.72rem;"><i class="bi bi-box-arrow-right"></i> Logout</button>
    </form>
  </div>
</div>

<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:#0d1b2a;font-size:1.05rem;"><i class="bi bi-people me-2"></i>User Management</span>
    <a href="<%=ctx%>/admin/users/new" class="btn btn-sm btn-primary"><i class="bi bi-person-plus me-1"></i>New User</a>
  </div>
  <div class="content-area">
<% if (error != null) { %>
    <div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=error%></div>
<% } %>
<% if (newScimToken != null) { %>
    <div class="alert alert-success alert-dismissible">
      <i class="bi bi-check-circle me-2"></i><strong>SCIM bearer token</strong> — copy it now, it is shown only once:
      <div class="d-flex align-items-center gap-2 mt-2">
        <span id="scimTok" style="font-family:monospace;font-size:.75rem;background:#f1f5f9;border:1px solid #dde4ee;border-radius:6px;padding:.35rem .6rem;word-break:break-all;" class="flex-grow-1"><%=esc(newScimToken)%></span>
        <button class="btn btn-sm btn-outline-success" onclick="navigator.clipboard.writeText(document.getElementById('scimTok').textContent);this.textContent='Copied!'">Copy</button>
      </div>
    </div>
<% } %>
    <div class="card mb-3" style="border:none;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);">
      <div class="card-body">
        <h6 class="fw-bold mb-2"><i class="bi bi-arrow-repeat me-2 text-primary"></i>SCIM 2.0 Provisioning</h6>
        <p style="font-size:.82rem;color:#555;margin-bottom:.5rem;">
          Base URL: <code><%=esc(scimBase)%></code> — authenticate with <code>Authorization: Bearer &lt;token&gt;</code>.
          Resources: <code>/Users</code>, <code>/Groups</code> (ADMIN/VIEWER), <code>/Certificates</code>, <code>/ApiClients</code>.
        </p>
        <div class="d-flex align-items-center gap-2">
          <span class="badge <%=scimConfigured?"bg-success":"bg-secondary"%>"><%=scimConfigured?"Enabled":"Disabled (no token)"%></span>
          <form method="post" action="<%=ctx%>/admin/users/scim-token" class="d-inline"
                onsubmit="return confirm('<%=scimConfigured?"Rotate":"Generate"%> the SCIM token? Any existing token stops working.')">
            <button class="btn btn-sm btn-outline-primary"><i class="bi bi-key me-1"></i><%=scimConfigured?"Rotate token":"Generate token"%></button>
          </form>
        </div>
      </div>
    </div>
    <div class="table-card">
<% if (users.isEmpty()) { %>
      <div class="text-center text-muted py-5">
        <i class="bi bi-people fs-2 d-block mb-2 opacity-25"></i>No users configured.
      </div>
<% } else { %>
      <div class="table-responsive">
        <table class="table table-hover">
          <thead><tr><th>Username</th><th>Display Name</th><th>Email</th><th>Role</th><th>Active</th><th>Last Login</th><th></th></tr></thead>
          <tbody>
<% for (PkiUser u : users) { %>
            <tr>
              <td class="fw-semibold"><%=esc(u.getUsername())%></td>
              <td><%=u.getDisplayName()!=null?esc(u.getDisplayName()):""%></td>
              <td style="font-size:.8rem;"><%=u.getEmail()!=null?esc(u.getEmail()):""%></td>
              <td><span class="badge <%=u.getRole()==PkiUser.Role.ADMIN?"bg-primary":"bg-secondary"%>"><%=u.getRole()%></span></td>
              <td><span class="badge <%=u.isActive()?"bg-success":"bg-danger"%>"><%=u.isActive()?"Active":"Disabled"%></span></td>
              <td style="font-size:.78rem;"><%=u.getLastLoginAt()!=null?u.getLastLoginAt():"Never"%></td>
              <td>
                <a href="<%=ctx%>/admin/users/<%=u.getId()%>/edit" class="btn btn-sm btn-outline-primary me-1">Edit</a>
<% if (me == null || !me.getId().equals(u.getId())) { %>
                <form method="post" action="<%=ctx%>/admin/users/<%=u.getId()%>/delete" class="d-inline"
                      onsubmit="return confirm('Delete user <%=esc(u.getUsername())%>?')">
                  <button class="btn btn-sm btn-outline-danger">Delete</button>
                </form>
<% } %>
              </td>
            </tr>
<% } %>
          </tbody>
        </table>
      </div>
<% } %>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
<%! private String esc(String s){if(s==null)return"";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
