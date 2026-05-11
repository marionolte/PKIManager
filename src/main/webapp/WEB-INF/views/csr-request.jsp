<%@ page contentType="text/html;charset=UTF-8" %>
<%
    String error = (String) request.getAttribute("error");
    String ctx   = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – Submit CSR</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-blue:#1b4f8a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;}
.topbar{background:var(--pki-blue);color:#fff;padding:1rem 2rem;display:flex;align-items:center;gap:12px;}
.topbar h5{margin:0;font-weight:700;}
.topbar small{opacity:.75;font-size:.78rem;}
.content{max-width:760px;margin:2rem auto;padding:0 1rem;}
.card{border:none;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.07);}
</style>
</head>
<body>
<div class="topbar">
  <i class="bi bi-shield-lock-fill fs-4" style="color:var(--pki-teal)"></i>
  <div><h5>PKI Manager</h5><small>CSR Signing Request</small></div>
</div>
<div class="content">
  <nav aria-label="breadcrumb" class="mt-3 mb-3">
    <ol class="breadcrumb">
      <li class="breadcrumb-item"><a href="<%=ctx%>/public/csr">Submit CSR</a></li>
    </ol>
  </nav>

  <div class="card p-4">
    <h5 class="fw-bold mb-1"><i class="bi bi-file-earmark-plus me-2 text-primary"></i>Submit a Certificate Signing Request</h5>
    <p class="text-muted mb-4" style="font-size:.875rem;">
      Paste your PKCS#10 CSR below. The PKI administrator will review and sign it.
      You will receive a tracking link to check status and download the signed certificate.
    </p>
<% if (error != null) { %>
    <div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=error%></div>
<% } %>
    <form method="post" action="<%=ctx%>/public/csr">
      <div class="row g-3 mb-3">
        <div class="col-sm-6">
          <label class="form-label fw-semibold" style="font-size:.875rem;">Your Name *</label>
          <input type="text" name="requesterName" class="form-control" required placeholder="e.g. John Smith"/>
        </div>
        <div class="col-sm-6">
          <label class="form-label fw-semibold" style="font-size:.875rem;">Email (for notifications)</label>
          <input type="email" name="requesterEmail" class="form-control" placeholder="john@example.com"/>
        </div>
      </div>
      <div class="mb-3">
        <label class="form-label fw-semibold" style="font-size:.875rem;">CSR (PEM format) *</label>
        <textarea name="csrPem" class="form-control font-monospace" rows="10" required
            placeholder="-----BEGIN CERTIFICATE REQUEST-----&#10;...&#10;-----END CERTIFICATE REQUEST-----"></textarea>
        <div class="form-text">Generate with: <code>openssl req -new -newkey rsa:4096 -nodes -keyout key.pem -out request.csr</code></div>
      </div>
      <div class="mb-4">
        <label class="form-label fw-semibold" style="font-size:.875rem;">Notes for administrator</label>
        <textarea name="notes" class="form-control" rows="2" placeholder="Purpose, deployment environment, etc."></textarea>
      </div>
      <button type="submit" class="btn btn-primary px-4"><i class="bi bi-send me-2"></i>Submit CSR</button>
    </form>
  </div>

  <div class="mt-3 text-center">
    <small class="text-muted">
      Already have a tracking token?
      <a href="#" onclick="var t=prompt('Enter tracking token:'); if(t) location='<%=ctx%>/public/csr/'+t; return false;">
        Check request status
      </a>
    </small>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
