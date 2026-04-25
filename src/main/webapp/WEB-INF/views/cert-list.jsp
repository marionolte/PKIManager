<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.CertificateRecord" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<% List<CertificateRecord> certs=(List<CertificateRecord>)request.getAttribute("certificates");
   if(certs==null)certs=java.util.Collections.emptyList();
   String ctx=request.getContextPath(); %>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – Certificates</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-dark:#0d1b2a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;}
.sidebar{position:fixed;top:0;left:0;width:240px;height:100vh;background:var(--pki-dark);display:flex;flex-direction:column;z-index:100;}
.sidebar-brand{padding:1.5rem 1.2rem;border-bottom:1px solid rgba(255,255,255,.08);}
.sidebar-brand h5{color:var(--pki-teal);font-weight:700;margin:0;font-size:.95rem;}
.nav-sect{padding:.5rem 1rem .2rem;font-size:.68rem;text-transform:uppercase;letter-spacing:.08em;color:#556;}
.sidebar .nav-link{color:#9bb;padding:.5rem 1.2rem;font-size:.875rem;border-radius:0;}
.sidebar .nav-link:hover,.sidebar .nav-link.active{background:rgba(0,180,216,.12);color:var(--pki-teal);}
.sidebar .nav-link i{width:20px;margin-right:8px;}
.main-content{margin-left:240px;min-height:100vh;}
.topbar{background:#fff;border-bottom:1px solid #dde4ee;padding:.75rem 2rem;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:50;}
.content-area{padding:2rem;}
.table-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);overflow:hidden;}
.table-card .table{margin:0;}
.table-card .table thead{background:var(--pki-dark);color:#cdd;font-size:.78rem;text-transform:uppercase;}
.table-card .table thead th{border:none;padding:.9rem 1rem;font-weight:500;}
.table-card .table tbody td{padding:.75rem 1rem;vertical-align:middle;font-size:.875rem;border-color:#f0f0f0;}
.table-card .table tbody tr:hover{background:#f7faff;}
.badge-valid{background:#d1fae5;color:#065f46;} .badge-revoked{background:#fee2e2;color:#991b1b;}
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
    <a href="<%=ctx%>/cert" class="nav-link active"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
  </nav>
  <div class="p-3" style="border-top:1px solid rgba(255,255,255,.06);font-size:.72rem;color:#556;"><i class="bi bi-info-circle me-1"></i>Based on marionolte/PKI</div>
</div>
<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:var(--pki-dark);"><i class="bi bi-file-earmark-lock2 me-2"></i>All Certificates</span>
    <div class="d-flex gap-2">
      <input type="text" id="fi" class="form-control form-control-sm" placeholder="🔍 Filter…" style="max-width:220px;" oninput="ft()"/>
      <a href="<%=ctx%>/cert/issue" class="btn btn-success btn-sm"><i class="bi bi-plus me-1"></i>Issue</a>
    </div>
  </div>
  <div class="content-area">
<% if (certs.isEmpty()) { %>
    <div class="text-center py-5">
      <i class="bi bi-file-earmark-lock2 fs-1 text-muted d-block mb-3 opacity-25"></i>
      <h5 class="text-muted">No certificates issued yet</h5>
      <a href="<%=ctx%>/cert/issue" class="btn btn-success mt-2"><i class="bi bi-plus-circle me-1"></i>Issue First Certificate</a>
    </div>
<% } else { %>
    <div class="table-card">
      <table class="table table-hover" id="ct">
        <thead><tr><th>Common Name</th><th>Type</th><th>Issuing CA</th><th>Serial</th><th>Valid Until</th><th>Status</th><th></th></tr></thead>
        <tbody>
<% for (CertificateRecord cert : certs) {
   boolean warn = cert.isExpiringSoon(); boolean exp = cert.isExpired();
   String stBadge = cert.getCertStatus()==CertificateRecord.CertStatus.VALID?"badge-valid":"badge-revoked";
%>
          <tr>
            <td class="fw-semibold">
              <a href="<%=ctx%>/cert/<%=cert.getId()%>" class="text-decoration-none"><%=e(cert.getCommonName())%></a>
              <% if(cert.getSanDns()!=null&&!cert.getSanDns().isEmpty()){ %><div class="text-muted" style="font-size:.72rem;">SAN: <%=e(cert.getSanDns())%></div><% } %>
            </td>
            <td><span class="badge bg-secondary bg-opacity-75 text-white" style="font-size:.68rem;"><%=cert.getCertType()%></span></td>
            <td style="font-size:.82rem;"><%=e(cert.getIssuingCaDisplayName())%></td>
            <td><code style="font-size:.7rem;"><%=e(cert.getSerialNumber())%></code></td>
            <td style="font-size:.82rem;" class="<%=warn?"text-warning fw-bold":exp?"text-danger":""%>"><%=cert.getValidUntil()!=null?cert.getValidUntil().toString().substring(0,10):""%></td>
            <td><span class="badge <%=stBadge%> rounded-pill" style="font-size:.68rem;"><%=cert.getCertStatus()%></span></td>
            <td>
              <a href="<%=ctx%>/cert/<%=cert.getId()%>" class="btn btn-sm btn-outline-primary py-0 px-2"><i class="bi bi-eye"></i></a>
              <a href="<%=ctx%>/cert/<%=cert.getId()%>/download.pem" class="btn btn-sm btn-outline-secondary py-0 px-2"><i class="bi bi-download"></i></a>
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
<script>function ft(){var q=document.getElementById('fi').value.toLowerCase();document.querySelectorAll('#ct tbody tr').forEach(r=>{r.style.display=r.textContent.toLowerCase().includes(q)?'':'none';});}</script>
</body></html>
