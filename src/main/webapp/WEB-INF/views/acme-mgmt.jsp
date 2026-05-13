<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.AcmeCertificate, com.macmario.services.pki.entity.PkiUser" %>
<%
    List<AcmeCertificate> acmeList = (List<AcmeCertificate>) request.getAttribute("acmeList");
    String ctx   = request.getContextPath();
    String error = (String) request.getAttribute("error");
    String info  = (String) request.getAttribute("info");
    PkiUser me   = (PkiUser) session.getAttribute("currentUser");
    if (acmeList == null) acmeList = java.util.Collections.emptyList();
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – ACME / Let's Encrypt</title>
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
.card{border:none;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);}
.table-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);overflow:hidden;}
.table-card .table{margin:0;}
.table-card .table thead{background:#dde4ee;color:#334155;font-size:.78rem;text-transform:uppercase;}
.table-card .table thead th{border:none;padding:.9rem 1rem;font-weight:500;}
.table-card .table tbody td{padding:.75rem 1rem;vertical-align:middle;font-size:.875rem;border-color:#f0f0f0;}
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
    <a href="<%=ctx%>/admin/users/" class="nav-link"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link active"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
    <a href="<%=ctx%>/admin/api-clients" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
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
    <span style="font-weight:600;color:#0d1b2a;font-size:1.05rem;"><i class="bi bi-lock-fill me-2"></i>ACME / Let's Encrypt</span>
  </div>
  <div class="content-area">
<% if (error != null) { %>
    <div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=error%></div>
<% } %>
<% if (info != null) { %>
    <div class="alert alert-info"><i class="bi bi-info-circle me-2"></i><%=info%></div>
<% } %>

    <div class="alert alert-warning mb-4" style="font-size:.85rem;">
      <i class="bi bi-exclamation-triangle me-2"></i>
      <strong>HTTP-01 Challenge requirement:</strong> The PKI server must be publicly reachable on port 80
      at the registered domain for Let's Encrypt to verify domain ownership.
      Staging mode uses the Let's Encrypt staging CA (untrusted, for testing).
    </div>

    <div class="row g-4">
      <!-- Register new domain -->
      <div class="col-lg-4">
        <div class="card p-4">
          <h6 class="fw-bold mb-3"><i class="bi bi-plus-circle me-2 text-primary"></i>Register Domain</h6>
          <form method="post" action="<%=ctx%>/admin/acme/register">
            <div class="mb-3">
              <label class="form-label fw-semibold" style="font-size:.875rem;">Domain *</label>
              <input type="text" name="domain" class="form-control" required placeholder="pki.example.com"/>
            </div>
            <div class="mb-3">
              <label class="form-label fw-semibold" style="font-size:.875rem;">Contact Email</label>
              <input type="email" name="contactEmail" class="form-control" placeholder="admin@example.com"/>
            </div>
            <div class="mb-4">
              <div class="form-check">
                <input class="form-check-input" type="checkbox" name="staging" value="true" id="stagingCheck" checked/>
                <label class="form-check-label" for="stagingCheck" style="font-size:.875rem;">
                  Use staging (test without rate limits)
                </label>
              </div>
            </div>
            <button type="submit" class="btn btn-primary w-100"><i class="bi bi-plus me-2"></i>Register</button>
          </form>
        </div>
      </div>

      <!-- Domain list -->
      <div class="col-lg-8">
        <div class="table-card">
          <div class="p-3 pb-0">
            <h6 class="fw-bold mb-0"><i class="bi bi-list-check me-2"></i>Managed Domains</h6>
          </div>
<% if (acmeList.isEmpty()) { %>
          <div class="text-center text-muted py-5">
            <i class="bi bi-lock fs-2 d-block mb-2 opacity-25"></i>No domains registered yet.
          </div>
<% } else { %>
          <div class="table-responsive">
            <table class="table table-hover mt-2">
              <thead><tr><th>Domain</th><th>Status</th><th>Valid Until</th><th>Last Renewed</th><th></th></tr></thead>
              <tbody>
<% for (AcmeCertificate a : acmeList) {
   String statusBadge = switch(a.getStatus()) {
       case ACTIVE  -> "bg-success";
       case ERROR   -> "bg-danger";
       case EXPIRED -> "bg-warning text-dark";
       default      -> "bg-secondary";
   };
%>
                <tr>
                  <td class="fw-semibold"><%=esc(a.getDomain())%></td>
                  <td><span class="badge <%=statusBadge%>"><%=a.getStatus()%></span></td>
                  <td style="font-size:.8rem;"><%=a.getValidUntil()!=null?a.getValidUntil():"—"%></td>
                  <td style="font-size:.8rem;"><%=a.getLastRenewedAt()!=null?a.getLastRenewedAt():"—"%></td>
                  <td>
                    <form method="post" action="<%=ctx%>/admin/acme/<%=a.getId()%>/request" class="d-inline">
                      <input type="hidden" name="staging" value="false"/>
                      <button class="btn btn-sm btn-outline-primary me-1" title="Request/Renew certificate">
                        <i class="bi bi-arrow-clockwise"></i>
                      </button>
                    </form>
<% if (a.getCertPem() != null) { %>
                    <a href="<%=ctx%>/cert" class="btn btn-sm btn-outline-secondary me-1" title="View certificate"></a>
<% } %>
                    <form method="post" action="<%=ctx%>/admin/acme/<%=a.getId()%>/delete" class="d-inline"
                          onsubmit="return confirm('Remove domain <%=esc(a.getDomain())%>?')">
                      <button class="btn btn-sm btn-outline-danger"><i class="bi bi-trash"></i></button>
                    </form>
                  </td>
                </tr>
<% if (a.getErrorMessage() != null && !a.getErrorMessage().isBlank()) { %>
                <tr>
                  <td colspan="5" class="text-danger" style="font-size:.78rem;">
                    <i class="bi bi-exclamation-circle me-1"></i><%=esc(a.getErrorMessage())%>
                  </td>
                </tr>
<% } %>
<% } %>
              </tbody>
            </table>
          </div>
<% } %>
        </div>
      </div>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
<%! private String esc(String s){if(s==null)return"";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
