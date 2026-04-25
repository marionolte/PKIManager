<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.CaConfig, com.macmario.services.pki.entity.CertificateRecord" %>
<%
    List<CaConfig> caList = (List<CaConfig>) request.getAttribute("caList");
    List<CertificateRecord> expiringSoon = (List<CertificateRecord>) request.getAttribute("expiringSoon");
    List<CertificateRecord> recentCerts = (List<CertificateRecord>) request.getAttribute("recentCerts");
    long totalCerts   = request.getAttribute("totalCerts")   != null ? (long)request.getAttribute("totalCerts")   : 0;
    long validCerts   = request.getAttribute("validCerts")   != null ? (long)request.getAttribute("validCerts")   : 0;
    long revokedCerts = request.getAttribute("revokedCerts") != null ? (long)request.getAttribute("revokedCerts") : 0;
    String ctx = request.getContextPath();
    String error = (String) request.getAttribute("error");
    if (caList   == null) caList   = java.util.Collections.emptyList();
    if (expiringSoon == null) expiringSoon = java.util.Collections.emptyList();
    if (recentCerts  == null) recentCerts  = java.util.Collections.emptyList();
%>
<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – Dashboard</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-dark:#0d1b2a;--pki-blue:#1b4f8a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
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
.stat-card{border:none;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);}
.stat-icon{width:48px;height:48px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:1.4rem;}
.si-blue{background:#e8f0fe;color:#1b4f8a;} .si-green{background:#e6f9f0;color:#1a8a4f;}
.si-red{background:#fdecea;color:#c0392b;}  .si-yellow{background:#fff8e1;color:#e6a817;}
.table-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);overflow:hidden;}
.table-card .table{margin:0;}
.table-card .table thead{background:var(--pki-dark);color:#cdd;font-size:.78rem;text-transform:uppercase;}
.table-card .table thead th{border:none;padding:.9rem 1rem;font-weight:500;}
.table-card .table tbody td{padding:.75rem 1rem;vertical-align:middle;font-size:.875rem;border-color:#f0f0f0;}
.table-card .table tbody tr:hover{background:#f7faff;}
.badge-valid{background:#d1fae5;color:#065f46;} .badge-revoked{background:#fee2e2;color:#991b1b;}
.badge-root{background:#dbeafe;color:#1e40af;} .badge-inter{background:#ede9fe;color:#5b21b6;}
.badge-issuing{background:#d1fae5;color:#065f46;}
.ca-tree-item{border-left:3px solid var(--pki-teal);background:#fff;border-radius:8px;margin-bottom:.5rem;padding:.75rem 1rem;}
.ca-tree-root{border-color:#1b4f8a;} .ca-tree-inter{border-color:#7c3aed;margin-left:1.5rem;}
.ca-tree-issuing{border-color:#059669;margin-left:3rem;}
.expiry-warn{background:#fff8e1;border:1px solid #ffc107;border-radius:8px;padding:.75rem 1rem;}
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
    <a href="<%=ctx%>/dashboard" class="nav-link active"><i class="bi bi-speedometer2"></i>Dashboard</a>
    <div class="nav-sect mt-2">PKI Hierarchy</div>
    <a href="<%=ctx%>/ca" class="nav-link"><i class="bi bi-diagram-3"></i>Certificate Authorities</a>
    <a href="<%=ctx%>/ca/create" class="nav-link"><i class="bi bi-plus-circle"></i>New CA</a>
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
  </nav>
  <div class="p-3" style="border-top:1px solid rgba(255,255,255,.06);font-size:.72rem;color:#556;">
    <i class="bi bi-info-circle me-1"></i>Based on marionolte/PKI
  </div>
</div>

<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:var(--pki-dark);font-size:1.05rem;"><i class="bi bi-speedometer2 me-2"></i>Dashboard</span>
    <span class="text-muted" style="font-size:.8rem;"><i class="bi bi-database me-1"></i>H2 &nbsp;|&nbsp;<i class="bi bi-shield-check me-1" style="color:var(--pki-teal)"></i>Bouncy Castle</span>
  </div>
  <div class="content-area">

<% if (error != null) { %>
    <div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=error%></div>
<% } %>

    <!-- Stat Cards -->
    <div class="row g-3 mb-4">
      <div class="col-sm-6 col-xl-3">
        <div class="card stat-card h-100"><div class="card-body d-flex align-items-center gap-3">
          <div class="stat-icon si-blue"><i class="bi bi-diagram-3"></i></div>
          <div><div style="font-size:1.8rem;font-weight:700;"><%=caList.size()%></div>
          <div class="text-muted" style="font-size:.8rem;">Certificate Authorities</div></div>
        </div></div>
      </div>
      <div class="col-sm-6 col-xl-3">
        <div class="card stat-card h-100"><div class="card-body d-flex align-items-center gap-3">
          <div class="stat-icon si-green"><i class="bi bi-patch-check"></i></div>
          <div><div style="font-size:1.8rem;font-weight:700;"><%=validCerts%></div>
          <div class="text-muted" style="font-size:.8rem;">Valid Certificates</div></div>
        </div></div>
      </div>
      <div class="col-sm-6 col-xl-3">
        <div class="card stat-card h-100"><div class="card-body d-flex align-items-center gap-3">
          <div class="stat-icon si-red"><i class="bi bi-x-circle"></i></div>
          <div><div style="font-size:1.8rem;font-weight:700;"><%=revokedCerts%></div>
          <div class="text-muted" style="font-size:.8rem;">Revoked Certificates</div></div>
        </div></div>
      </div>
      <div class="col-sm-6 col-xl-3">
        <div class="card stat-card h-100"><div class="card-body d-flex align-items-center gap-3">
          <div class="stat-icon si-yellow"><i class="bi bi-clock-history"></i></div>
          <div><div style="font-size:1.8rem;font-weight:700;"><%=expiringSoon.size()%></div>
          <div class="text-muted" style="font-size:.8rem;">Expiring within 30 days</div></div>
        </div></div>
      </div>
    </div>

<% if (!expiringSoon.isEmpty()) { %>
    <div class="expiry-warn mb-4">
      <div class="d-flex align-items-center gap-2 mb-2">
        <i class="bi bi-exclamation-triangle-fill text-warning"></i>
        <strong>Certificates expiring within 30 days</strong>
      </div>
<% for (CertificateRecord cert : expiringSoon) { %>
      <div class="d-flex align-items-center justify-content-between py-1">
        <span><i class="bi bi-file-earmark-lock me-1"></i>
          <a href="<%=ctx%>/cert/<%=cert.getId()%>"><%=esc(cert.getCommonName())%></a>
        </span>
        <span class="text-muted" style="font-size:.8rem;">Expires: <%=cert.getValidUntil()%></span>
      </div>
<% } %>
    </div>
<% } %>

    <div class="row g-4">
      <!-- CA Hierarchy -->
      <div class="col-lg-5">
        <div class="table-card p-3">
          <div class="d-flex align-items-center justify-content-between mb-3">
            <h6 class="mb-0 fw-bold"><i class="bi bi-diagram-3 me-2 text-primary"></i>CA Hierarchy</h6>
            <a href="<%=ctx%>/ca/create" class="btn btn-sm btn-primary" style="font-size:.75rem;"><i class="bi bi-plus"></i> New CA</a>
          </div>
<% if (caList.isEmpty()) { %>
          <div class="text-center text-muted py-4">
            <i class="bi bi-diagram-3 fs-2 d-block mb-2 opacity-25"></i>
            No CAs configured.<br/><a href="<%=ctx%>/ca/create">Create your Root CA</a>
          </div>
<% } else { for (CaConfig ca : caList) {
     String typeClass = ca.getCaType() == CaConfig.CaType.ROOT ? "ca-tree-root" :
                        ca.getCaType() == CaConfig.CaType.INTERMEDIATE ? "ca-tree-inter" : "ca-tree-issuing";
     String typeBadge = ca.getCaType() == CaConfig.CaType.ROOT ? "badge-root" :
                        ca.getCaType() == CaConfig.CaType.INTERMEDIATE ? "badge-inter" : "badge-issuing";
     String statusBadge = ca.getStatus() == CaConfig.CaStatus.DISABLED ? "bg-secondary" :
                          ca.isExpired() ? "bg-danger" : "bg-success";
     String statusLabel = ca.getStatus() == CaConfig.CaStatus.DISABLED ? "Disabled" :
                          ca.isExpired() ? "Expired" : "Active";
%>
          <div class="ca-tree-item <%=typeClass%>">
            <div class="d-flex align-items-center justify-content-between">
              <div>
                <span class="badge <%=typeBadge%> rounded-pill" style="font-size:.65rem;"><%=ca.getCaType()%></span>
                <a href="<%=ctx%>/ca/<%=ca.getId()%>" class="fw-semibold ms-1 text-decoration-none" style="font-size:.875rem;"><%=esc(ca.getDisplayName())%></a>
              </div>
              <span class="badge <%=statusBadge%>" style="font-size:.65rem;"><%=statusLabel%></span>
            </div>
            <small class="text-muted d-block mt-1" style="font-size:.72rem;">CN: <%=esc(ca.getCommonName())%></small>
          </div>
<% } } %>
        </div>
      </div>

      <!-- Recent Certificates -->
      <div class="col-lg-7">
        <div class="table-card">
          <div class="d-flex align-items-center justify-content-between p-3 pb-0">
            <h6 class="mb-0 fw-bold"><i class="bi bi-file-earmark-lock2 me-2 text-success"></i>Recent Certificates</h6>
            <a href="<%=ctx%>/cert/issue" class="btn btn-sm btn-success" style="font-size:.75rem;"><i class="bi bi-plus"></i> Issue</a>
          </div>
<% if (recentCerts.isEmpty()) { %>
          <div class="text-center text-muted py-5">
            <i class="bi bi-file-earmark-lock2 fs-2 d-block mb-2 opacity-25"></i>No certificates issued yet.
          </div>
<% } else { %>
          <div class="table-responsive">
            <table class="table table-hover">
              <thead><tr><th>Common Name</th><th>Type</th><th>Expires</th><th>Status</th></tr></thead>
              <tbody>
<% for (CertificateRecord cert : recentCerts) { %>
                <tr>
                  <td><a href="<%=ctx%>/cert/<%=cert.getId()%>" class="text-decoration-none fw-semibold"><%=esc(cert.getCommonName())%></a></td>
                  <td><span class="badge bg-secondary bg-opacity-75 text-white" style="font-size:.68rem;"><%=cert.getCertType()%></span></td>
                  <td style="font-size:.8rem;" class="<%=cert.isExpiringSoon()?"text-warning fw-bold":""%>"><%=cert.getValidUntil()%></td>
                  <td><span class="badge <%=cert.getCertStatus()==CertificateRecord.CertStatus.VALID?"badge-valid":"badge-revoked"%> rounded-pill" style="font-size:.68rem;"><%=cert.getCertStatus()%></span></td>
                </tr>
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
<%! private String esc(String s) { if (s==null) return ""; return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); } %>
