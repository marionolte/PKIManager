<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.CaConfig, com.macmario.services.pki.entity.PkiUser" %>
<%
    List<CaConfig> caList = (List<CaConfig>) request.getAttribute("caList");
    if (caList == null) caList = java.util.Collections.emptyList();
    String ctx = request.getContextPath();
    PkiUser me = (PkiUser) session.getAttribute("currentUser");
%><%! private String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – Certificate Authorities</title>
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
.badge-root{background:#dbeafe;color:#1e40af;} .badge-inter{background:#ede9fe;color:#5b21b6;} .badge-issuing{background:#d1fae5;color:#065f46;}
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
    <a href="<%=ctx%>/ca" class="nav-link active"><i class="bi bi-diagram-3"></i>Certificate Authorities</a>
    <a href="<%=ctx%>/ca/create" class="nav-link"><i class="bi bi-plus-circle"></i>New CA</a>
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
    <div class="nav-sect mt-2">Requests</div>
    <a href="<%=ctx%>/admin/csr-jobs" class="nav-link"><i class="bi bi-inbox"></i>CSR Jobs</a>
    <div class="nav-sect mt-2">Administration</div>
    <a href="<%=ctx%>/admin/users/" class="nav-link"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
    <a href="<%=ctx%>/admin/api-clients" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
  </nav>
  <div class="p-3" style="border-top:1px solid rgba(0,0,0,.08);font-size:.72rem;color:#64748b;">
    <i class="bi bi-person-circle me-1"></i><%=me!=null?me.getDisplayName():""%>
    <form method="post" action="<%=ctx%>/logout" class="d-inline ms-2">
      <button class="btn btn-link btn-sm p-0 text-danger" style="font-size:.72rem;"><i class="bi bi-box-arrow-right"></i> Logout</button>
    </form>
  </div>
</div>
<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:#0d1b2a;"><i class="bi bi-diagram-3 me-2"></i>Certificate Authorities</span>
    <a href="<%=ctx%>/ca/create" class="btn btn-primary btn-sm"><i class="bi bi-plus-circle me-1"></i>New CA</a>
  </div>
  <div class="content-area">
<% if (caList.isEmpty()) { %>
    <div class="text-center py-5">
      <i class="bi bi-diagram-3 fs-1 text-muted d-block mb-3 opacity-25"></i>
      <h5 class="text-muted">No Certificate Authorities configured</h5>
      <a href="<%=ctx%>/ca/create" class="btn btn-primary mt-2"><i class="bi bi-plus-circle me-1"></i>Create Root CA</a>
    </div>
<% } else { %>
    <div class="table-card">
      <table class="table table-hover">
        <thead><tr><th>Role Name</th><th>Display Name</th><th>Common Name</th><th>Type</th><th>Status</th><th>Valid Until</th><th>Actions</th></tr></thead>
        <tbody>
<% for (CaConfig ca : caList) {
   String typeBadge = ca.getCaType()==CaConfig.CaType.ROOT?"badge-root":ca.getCaType()==CaConfig.CaType.INTERMEDIATE?"badge-inter":"badge-issuing";
   String statusBadge = ca.getStatus()==CaConfig.CaStatus.DISABLED?"bg-secondary":ca.isExpired()?"bg-danger":ca.isExpiringSoon()?"bg-warning text-dark":"bg-success";
   String statusLabel = ca.getStatus()==CaConfig.CaStatus.DISABLED?"Disabled":ca.isExpired()?"Expired":ca.isExpiringSoon()?"Expiring Soon":"Active";
%>
          <tr>
            <td><code style="font-size:.8rem;"><%=esc(ca.getRoleName())%></code></td>
            <td class="fw-semibold"><%=esc(ca.getDisplayName())%></td>
            <td class="text-muted" style="font-size:.82rem;"><%=esc(ca.getCommonName())%></td>
            <td><span class="badge <%=typeBadge%> rounded-pill" style="font-size:.68rem;"><%=ca.getCaType()%></span></td>
            <td><span class="badge <%=statusBadge%>"><%=statusLabel%></span></td>
            <td style="font-size:.82rem;"><%=ca.getValidUntil()!=null?ca.getValidUntil().toString().substring(0,10):""%></td>
            <td>
              <a href="<%=ctx%>/ca/<%=ca.getId()%>" class="btn btn-sm btn-outline-primary py-0 px-2"><i class="bi bi-eye"></i></a>
              <a href="<%=ctx%>/ca/<%=ca.getId()%>/cert.pem" class="btn btn-sm btn-outline-secondary py-0 px-2"><i class="bi bi-download"></i></a>
            </td>
          </tr>
<% } %>
        </tbody>
      </table>
    </div>
<% } %>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
