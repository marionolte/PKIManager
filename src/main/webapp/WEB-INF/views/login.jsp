<%@ page contentType="text/html;charset=UTF-8" %>
<%
    String error = (String) request.getAttribute("error");
    String ctx   = request.getContextPath();
    String next  = request.getParameter("next");
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – Login</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-blue:#1b4f8a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;}
.login-card{width:100%;max-width:420px;border:none;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,.10);}
.login-header{background:var(--pki-blue);border-radius:16px 16px 0 0;padding:2rem;text-align:center;color:#fff;}
.login-header h4{margin:0;font-weight:700;}
.login-header small{opacity:.75;font-size:.8rem;}
.login-body{padding:2rem;}
.btn-login{background:var(--pki-blue);color:#fff;border:none;width:100%;padding:.75rem;border-radius:8px;font-weight:600;}
.btn-login:hover{background:#16407a;color:#fff;}
.public-links{margin-top:1.5rem;padding-top:1rem;border-top:1px solid #e2e8f0;font-size:.82rem;text-align:center;}
</style>
</head>
<body>
<div class="login-card card">
  <div class="login-header">
    <i class="bi bi-shield-lock-fill fs-2 mb-2 d-block" style="color:var(--pki-teal)"></i>
    <h4>PKI Manager</h4>
    <small>Internal Certificate Authority</small>
  </div>
  <div class="login-body">
<% if (error != null) { %>
    <div class="alert alert-danger py-2"><i class="bi bi-exclamation-triangle me-2"></i><%=error%></div>
<% } %>
    <form method="post" action="<%=ctx%>/login">
      <% if (next != null) { %><input type="hidden" name="next" value="<%=next%>"/><% } %>
      <div class="mb-3">
        <label class="form-label fw-semibold" style="font-size:.875rem;">Username</label>
        <div class="input-group">
          <span class="input-group-text"><i class="bi bi-person"></i></span>
          <input type="text" name="username" class="form-control" autofocus required/>
        </div>
      </div>
      <div class="mb-4">
        <label class="form-label fw-semibold" style="font-size:.875rem;">Password</label>
        <div class="input-group">
          <span class="input-group-text"><i class="bi bi-lock"></i></span>
          <input type="password" name="password" class="form-control" required/>
        </div>
      </div>
      <button type="submit" class="btn btn-login"><i class="bi bi-box-arrow-in-right me-2"></i>Sign in</button>
    </form>
    <div class="public-links">
      <a href="<%=ctx%>/public/csr"><i class="bi bi-file-earmark-plus me-1"></i>Submit a CSR for signing</a>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
