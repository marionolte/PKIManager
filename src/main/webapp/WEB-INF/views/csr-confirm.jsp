<%@ page contentType="text/html;charset=UTF-8" %>
<%
    String token    = (String) request.getAttribute("token");
    String subject  = (String) request.getAttribute("subjectCn");
    String ctx      = request.getContextPath();
    String statusUrl = ctx + "/public/csr/" + token;
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – CSR Submitted</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-blue:#1b4f8a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;}
.topbar{background:var(--pki-blue);color:#fff;padding:1rem 2rem;display:flex;align-items:center;gap:12px;}
.content{max-width:660px;margin:2rem auto;padding:0 1rem;}
.card{border:none;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.07);}
.token-box{background:#f0f4f8;border:1px solid #cdd8e8;border-radius:8px;padding:.75rem 1rem;font-family:monospace;font-size:.9rem;word-break:break-all;}
</style>
</head>
<body>
<div class="topbar">
  <i class="bi bi-shield-lock-fill fs-4" style="color:var(--pki-teal)"></i>
  <div><h5 style="margin:0;font-weight:700;">PKI Manager</h5><small style="opacity:.75;font-size:.78rem;">CSR Submitted</small></div>
</div>
<div class="content mt-4">
  <div class="card p-4 text-center">
    <i class="bi bi-check-circle-fill fs-1 text-success mb-3"></i>
    <h5 class="fw-bold">CSR Submitted Successfully</h5>
    <p class="text-muted mb-4" style="font-size:.875rem;">
      Your request for <strong><%=subject != null ? subject : "Unknown CN" %></strong>
      has been received and is awaiting PKI administrator review.
    </p>
    <div class="text-start mb-4">
      <div class="mb-2 fw-semibold" style="font-size:.875rem;">Your tracking token:</div>
      <div class="token-box mb-2"><%=token%></div>
      <div class="d-grid">
        <a href="<%=statusUrl%>" class="btn btn-primary"><i class="bi bi-arrow-right-circle me-2"></i>View Request Status</a>
      </div>
    </div>
    <div class="alert alert-warning text-start py-2" style="font-size:.8rem;">
      <i class="bi bi-exclamation-triangle me-2"></i>
      <strong>Save this link:</strong><br/>
      <a href="<%=statusUrl%>" style="word-break:break-all;"><%=request.getScheme()+"://"+request.getServerName()+(request.getServerPort()!=80&&request.getServerPort()!=443?":"+request.getServerPort():"")+statusUrl%></a>
    </div>
    <a href="<%=ctx%>/public/csr" class="btn btn-outline-secondary btn-sm mt-2">Submit another CSR</a>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
