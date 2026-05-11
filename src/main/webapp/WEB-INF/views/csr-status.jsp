<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.macmario.services.pki.entity.CsrRequest" %>
<%
    CsrRequest csr = (CsrRequest) request.getAttribute("csrRequest");
    String ctx = request.getContextPath();
    boolean signed   = csr != null && csr.getStatus() == CsrRequest.Status.SIGNED;
    boolean rejected = csr != null && csr.getStatus() == CsrRequest.Status.REJECTED;
    boolean pending  = csr != null && csr.getStatus() == CsrRequest.Status.PENDING;
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – Request Status</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-blue:#1b4f8a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;}
.topbar{background:var(--pki-blue);color:#fff;padding:1rem 2rem;display:flex;align-items:center;gap:12px;}
.content{max-width:720px;margin:2rem auto;padding:0 1rem;}
.card{border:none;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.07);}
</style>
</head>
<body>
<div class="topbar">
  <i class="bi bi-shield-lock-fill fs-4" style="color:var(--pki-teal)"></i>
  <div><h5 style="margin:0;font-weight:700;">PKI Manager</h5><small style="opacity:.75;font-size:.78rem;">CSR Request Status</small></div>
</div>
<div class="content mt-4">
<% if (csr == null) { %>
  <div class="alert alert-danger"><i class="bi bi-x-circle me-2"></i>Request not found. Check the tracking token.</div>
<% } else { %>
  <div class="card p-4 mb-3">
    <div class="d-flex align-items-center gap-3 mb-3">
<% if (signed) { %>
      <i class="bi bi-check-circle-fill text-success fs-2"></i>
      <div><div class="fw-bold">Certificate Signed</div><small class="text-muted">Your certificate is ready for download.</small></div>
<% } else if (rejected) { %>
      <i class="bi bi-x-circle-fill text-danger fs-2"></i>
      <div><div class="fw-bold text-danger">Request Rejected</div><small class="text-muted">The PKI administrator rejected this request.</small></div>
<% } else { %>
      <i class="bi bi-hourglass-split text-warning fs-2"></i>
      <div><div class="fw-bold">Awaiting Review</div><small class="text-muted">Your request is in the queue.</small></div>
<% } %>
    </div>
    <table class="table table-sm mb-0">
      <tr><th style="width:160px;font-size:.8rem;">Subject CN</th><td><%=csr.getSubjectCn()%></td></tr>
      <tr><th style="font-size:.8rem;">Requester</th><td><%=csr.getRequesterName() != null ? csr.getRequesterName() : "—"%></td></tr>
      <tr><th style="font-size:.8rem;">Submitted</th><td style="font-size:.85rem;"><%=csr.getRequestedAt()%></td></tr>
      <tr><th style="font-size:.8rem;">Status</th>
          <td><span class="badge <%=signed?"bg-success":rejected?"bg-danger":"bg-warning text-dark"%>"><%=csr.getStatus()%></span></td></tr>
<% if (signed && csr.getIssuingCaName() != null) { %>
      <tr><th style="font-size:.8rem;">Signed by CA</th><td><%=csr.getIssuingCaName()%></td></tr>
      <tr><th style="font-size:.8rem;">Signed at</th><td style="font-size:.85rem;"><%=csr.getProcessedAt()%></td></tr>
<% } %>
<% if (csr.getAdminNotes() != null && !csr.getAdminNotes().isBlank()) { %>
      <tr><th style="font-size:.8rem;">Admin notes</th><td style="font-size:.85rem;"><%=csr.getAdminNotes()%></td></tr>
<% } %>
    </table>
  </div>

<% if (signed && csr.getSignedCertId() != null) { %>
  <div class="card p-4 border-success" style="border:1.5px solid #198754!important;">
    <h6 class="fw-bold mb-3"><i class="bi bi-download me-2 text-success"></i>Download Your Certificate</h6>
    <p class="text-muted mb-3" style="font-size:.875rem;">
      Use the public download link below. You can share this link — it does not require a login.
    </p>
    <div class="d-flex gap-2">
      <a href="<%=ctx%>/cert/<%=csr.getSignedCertId()%>" class="btn btn-outline-secondary btn-sm">
        <i class="bi bi-eye me-1"></i>View certificate details
      </a>
    </div>
  </div>
<% } %>
<% } %>
  <div class="mt-3 text-center">
    <a href="<%=ctx%>/public/csr" class="btn btn-outline-secondary btn-sm">Submit another CSR</a>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
