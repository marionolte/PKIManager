<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.*, com.macmario.services.pki.entity.CsrRequest.Status" %>
<%
    CsrRequest csr = (CsrRequest) request.getAttribute("csrRequest");
    List<CaConfig> issuingCas = (List<CaConfig>) request.getAttribute("issuingCas");
    String ctx   = request.getContextPath();
    String error = (String) request.getAttribute("error");
    PkiUser me   = (PkiUser) session.getAttribute("currentUser");
    if (issuingCas == null) issuingCas = java.util.Collections.emptyList();
    boolean isPending = csr != null && csr.getStatus() == Status.PENDING;
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – CSR Job Detail</title>
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
    <a href="<%=ctx%>/admin/csr-jobs" class="nav-link active"><i class="bi bi-inbox"></i>CSR Jobs</a>
    <div class="nav-sect mt-2">Administration</div>
    <a href="<%=ctx%>/admin/users/" class="nav-link"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
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
    <span style="font-weight:600;color:#0d1b2a;font-size:1.05rem;">
      <i class="bi bi-inbox me-2"></i>CSR Job #<%=csr!=null?csr.getId():""%>
    </span>
    <a href="<%=ctx%>/admin/csr-jobs" class="btn btn-sm btn-outline-secondary"><i class="bi bi-arrow-left me-1"></i>Back to jobs</a>
  </div>
  <div class="content-area">
<% if (error != null) { %>
    <div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=error%></div>
<% } %>
<% if (csr == null) { %>
    <div class="alert alert-warning">CSR request not found.</div>
<% } else { %>
    <div class="row g-4">
      <div class="col-lg-6">
        <div class="card p-4">
          <h6 class="fw-bold mb-3">Request Details</h6>
          <table class="table table-sm mb-0">
            <tr><th style="width:130px;font-size:.8rem;">Subject CN</th><td class="fw-semibold"><%=esc(csr.getSubjectCn())%></td></tr>
            <tr><th style="font-size:.8rem;">Requester</th><td><%=csr.getRequesterName()!=null?esc(csr.getRequesterName()):"—"%></td></tr>
            <tr><th style="font-size:.8rem;">Email</th><td><%=csr.getRequesterEmail()!=null?esc(csr.getRequesterEmail()):"—"%></td></tr>
            <tr><th style="font-size:.8rem;">Submitted</th><td style="font-size:.8rem;"><%=csr.getRequestedAt()%></td></tr>
            <tr><th style="font-size:.8rem;">Status</th>
                <td><span class="badge <%=csr.getStatus()==Status.SIGNED?"bg-success":csr.getStatus()==Status.REJECTED?"bg-danger":"bg-warning text-dark"%>"><%=csr.getStatus()%></span></td></tr>
          </table>
<% if (csr.getRequesterNotes() != null && !csr.getRequesterNotes().isBlank()) { %>
          <div class="mt-3"><div class="fw-semibold" style="font-size:.8rem;">Requester notes:</div>
          <div class="bg-light rounded p-2 mt-1" style="font-size:.83rem;"><%=esc(csr.getRequesterNotes())%></div></div>
<% } %>
        </div>

        <div class="card p-4 mt-3">
          <h6 class="fw-bold mb-2">CSR (PEM)</h6>
          <textarea class="form-control font-monospace" rows="8" readonly style="font-size:.72rem;"><%=esc(csr.getCsrPem())%></textarea>
        </div>
      </div>

      <div class="col-lg-6">
<% if (isPending) { %>
        <!-- Sign form -->
        <div class="card p-4 border-primary" style="border:1.5px solid #1b4f8a!important;">
          <h6 class="fw-bold mb-3 text-primary"><i class="bi bi-pen me-2"></i>Sign this CSR</h6>
          <form method="post" action="<%=ctx%>/admin/csr-jobs/<%=csr.getId()%>/sign">
            <div class="mb-3">
              <label class="form-label fw-semibold" style="font-size:.875rem;">Issuing CA *</label>
              <select name="caId" class="form-select" required>
                <option value="">— select CA —</option>
<% for (CaConfig ca : issuingCas) { %>
                <option value="<%=ca.getId()%>"><%=esc(ca.getDisplayName())%> (<%=ca.getCaType()%>)</option>
<% } %>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label fw-semibold" style="font-size:.875rem;">Certificate Type</label>
              <select name="certType" class="form-select">
                <option value="SERVER">SERVER</option>
                <option value="CLIENT">CLIENT</option>
                <option value="CODE_SIGNING">CODE_SIGNING</option>
                <option value="EMAIL">EMAIL</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label fw-semibold" style="font-size:.875rem;">Admin notes</label>
              <textarea name="adminNotes" class="form-control" rows="2" placeholder="Optional notes for the requester"></textarea>
            </div>
            <button type="submit" class="btn btn-success w-100"><i class="bi bi-check-lg me-2"></i>Sign CSR</button>
          </form>
        </div>
        <!-- Reject form -->
        <div class="card p-4 mt-3 border-danger" style="border:1.5px solid #dc3545!important;">
          <h6 class="fw-bold mb-3 text-danger"><i class="bi bi-x-circle me-2"></i>Reject this request</h6>
          <form method="post" action="<%=ctx%>/admin/csr-jobs/<%=csr.getId()%>/reject">
            <div class="mb-3">
              <label class="form-label fw-semibold" style="font-size:.875rem;">Reason *</label>
              <textarea name="adminNotes" class="form-control" rows="2" required placeholder="Explain why the request is rejected"></textarea>
            </div>
            <button type="submit" class="btn btn-danger w-100"><i class="bi bi-x-lg me-2"></i>Reject</button>
          </form>
        </div>
<% } else { %>
        <div class="card p-4">
          <h6 class="fw-bold mb-3">Processing Result</h6>
          <table class="table table-sm mb-0">
            <tr><th style="font-size:.8rem;">Processed at</th><td style="font-size:.8rem;"><%=csr.getProcessedAt()%></td></tr>
<% if (csr.getIssuingCaName() != null) { %>
            <tr><th style="font-size:.8rem;">Signed by CA</th><td><%=esc(csr.getIssuingCaName())%></td></tr>
<% } %>
          </table>
<% if (csr.getAdminNotes() != null && !csr.getAdminNotes().isBlank()) { %>
          <div class="mt-3"><div class="fw-semibold" style="font-size:.8rem;">Admin notes:</div>
          <div class="bg-light rounded p-2 mt-1" style="font-size:.83rem;"><%=esc(csr.getAdminNotes())%></div></div>
<% } %>
<% if (csr.getSignedCertId() != null) { %>
          <div class="mt-3">
            <a href="<%=ctx%>/cert/<%=csr.getSignedCertId()%>" class="btn btn-outline-primary btn-sm">
              <i class="bi bi-file-earmark-lock me-1"></i>View issued certificate
            </a>
          </div>
<% } %>
        </div>
<% } %>
      </div>
    </div>
<% } %>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
<%! private String esc(String s){if(s==null)return"";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
