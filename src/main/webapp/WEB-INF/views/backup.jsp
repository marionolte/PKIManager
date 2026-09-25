<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, java.nio.file.Path, java.nio.file.Files, com.macmario.services.pki.entity.PkiUser" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private String badge(String n){ if(n.startsWith("auto-"))return "bg-info"; if(n.startsWith("pre-import-"))return "bg-warning text-dark"; return "bg-secondary"; }
    private String kind(String n){ if(n.startsWith("auto-"))return "auto"; if(n.startsWith("pre-import-"))return "pre-import"; return "manual"; } %>
<% List<Path> backups=(List<Path>)request.getAttribute("backups");
   if(backups==null)backups=java.util.Collections.emptyList();
   boolean autoEnabled = request.getAttribute("autoEnabled")!=null && (Boolean)request.getAttribute("autoEnabled");
   String autoTime = request.getAttribute("autoTime")!=null?(String)request.getAttribute("autoTime"):"22:05";
   int autoKeep = request.getAttribute("autoKeep")!=null?(int)request.getAttribute("autoKeep"):20;
   String ctx=request.getContextPath();
   String msg=request.getParameter("msg");
   String error=request.getParameter("error");
   if(error==null) error=(String)request.getAttribute("error");
   PkiUser me=(PkiUser)session.getAttribute("currentUser");
%>
<!DOCTYPE html><html lang="en">
<head><meta charset="UTF-8"/><title>PKI Manager – Backup &amp; Restore</title>
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
.card-box{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);padding:1.5rem;margin-bottom:1.5rem;}
.table-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);overflow:hidden;}
.table-card .table{margin:0;} .table-card .table thead{background:#dde4ee;color:#334155;font-size:.78rem;text-transform:uppercase;}
.table-card .table thead th{border:none;padding:.9rem 1rem;font-weight:500;}
.table-card .table tbody td{padding:.7rem 1rem;vertical-align:middle;font-size:.85rem;border-color:#f0f0f0;}
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
    <a href="<%=ctx%>/api-clients/" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/admin/backup/" class="nav-link active"><i class="bi bi-hdd-stack"></i>Backup &amp; Restore</a>
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
    <span style="font-weight:600;color:#0d1b2a;"><i class="bi bi-hdd-stack me-2"></i>Backup &amp; Restore</span>
    <a href="<%=ctx%>/admin/backup/export" class="btn btn-success btn-sm"><i class="bi bi-download me-1"></i>Export Now</a>
  </div>
  <div class="content-area">
<% if(msg!=null){ %><div class="alert alert-success"><i class="bi bi-check-circle me-2"></i><%=e(msg)%></div><% } %>
<% if(error!=null){ %><div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>

    <div class="row g-4">
      <div class="col-lg-6">
        <div class="card-box">
          <h6 class="fw-bold mb-2"><i class="bi bi-download me-2 text-success"></i>Export</h6>
          <p style="font-size:.85rem;color:#555;">Download a complete JSON backup of every table (CAs, certificates, users, API clients, CSR jobs, ACME, configuration). Includes private keys — store it securely.</p>
          <div class="d-flex gap-2">
            <a href="<%=ctx%>/admin/backup/export" class="btn btn-success btn-sm"><i class="bi bi-download me-1"></i>Download Backup</a>
            <form method="post" action="<%=ctx%>/admin/backup/create" class="d-inline">
              <button class="btn btn-outline-secondary btn-sm"><i class="bi bi-save me-1"></i>Save to Server</button>
            </form>
          </div>
        </div>

        <div class="card-box">
          <h6 class="fw-bold mb-2"><i class="bi bi-clock-history me-2 text-info"></i>Automatic Backups</h6>
          <p style="font-size:.85rem;color:#555;">Runs <strong>once a day</strong> at the time below, and writes a backup only if the stored data changed since the last one.</p>
          <form method="post" action="<%=ctx%>/admin/backup/settings">
            <div class="form-check form-switch mb-3">
              <input class="form-check-input" type="checkbox" role="switch" id="enabled" name="enabled" value="true" <%=autoEnabled?"checked":""%>/>
              <label class="form-check-label" for="enabled">Enable daily automatic backup</label>
            </div>
            <div class="row g-2 align-items-end">
              <div class="col-6">
                <label class="form-label fw-semibold" style="font-size:.8rem;">Daily time (HH:mm)</label>
                <input type="time" name="time" class="form-control form-control-sm" value="<%=e(autoTime)%>"/>
              </div>
              <div class="col-6">
                <label class="form-label fw-semibold" style="font-size:.8rem;">Auto-backups to keep</label>
                <input type="number" name="keep" class="form-control form-control-sm" min="1" max="500" value="<%=autoKeep%>"/>
              </div>
            </div>
            <button class="btn btn-primary btn-sm mt-3"><i class="bi bi-save me-1"></i>Save Settings</button>
          </form>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="card-box" style="border:1px solid #f5c2c7;">
          <h6 class="fw-bold mb-2 text-danger"><i class="bi bi-upload me-2"></i>Restore (Import)</h6>
          <div class="alert alert-danger py-2" style="font-size:.82rem;">
            <i class="bi bi-exclamation-triangle-fill me-1"></i>
            Importing <strong>replaces the entire database</strong> with the backup's contents. A
            <code>pre-import</code> safety backup is written first. This cannot be undone from the UI.
          </div>
          <form method="post" action="<%=ctx%>/admin/backup/import" enctype="multipart/form-data"
                onsubmit="return confirm('Replace ALL current data with this backup? A pre-import backup will be saved first.');">
            <div class="mb-3">
              <label class="form-label fw-semibold">Backup file (.json)</label>
              <input type="file" name="backupFile" accept=".json,application/json" class="form-control" required/>
            </div>
            <div class="mb-3">
              <label class="form-label fw-semibold">Confirm your password</label>
              <input type="password" name="password" class="form-control" autocomplete="current-password" required/>
            </div>
            <button class="btn btn-danger"><i class="bi bi-upload me-1"></i>Restore Database</button>
          </form>
        </div>
      </div>
    </div>

    <h6 class="fw-bold mt-2 mb-3"><i class="bi bi-archive me-2"></i>Stored Backups on Server</h6>
    <div class="table-card">
      <table class="table table-hover">
        <thead><tr><th>File</th><th>Type</th><th>Size</th><th></th></tr></thead>
        <tbody>
<% if(backups.isEmpty()){ %>
          <tr><td colspan="4" class="text-center text-muted py-4">No backups on server yet.</td></tr>
<% } else { for(Path p : backups){ String name=p.getFileName().toString();
       long size=0; try{ size=Files.size(p);}catch(Exception ex){} %>
          <tr>
            <td class="fw-semibold" style="font-family:monospace;"><%=e(name)%></td>
            <td><span class="badge <%=badge(name)%>"><%=kind(name)%></span></td>
            <td><%=size/1024%> KB</td>
            <td><a href="<%=ctx%>/admin/backup/download?name=<%=e(name)%>" class="btn btn-sm btn-outline-primary py-0 px-2"><i class="bi bi-download"></i></a></td>
          </tr>
<% } } %>
        </tbody>
      </table>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
