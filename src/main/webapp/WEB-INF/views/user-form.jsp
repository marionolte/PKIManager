<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.macmario.services.pki.entity.PkiUser" %>
<%
    PkiUser user = (PkiUser) request.getAttribute("user");
    boolean isEdit = user != null;
    String ctx   = request.getContextPath();
    String error = (String) request.getAttribute("error");
    PkiUser me   = (PkiUser) session.getAttribute("currentUser");
    String action = isEdit ? ctx + "/admin/users/" + user.getId() : ctx + "/admin/users/";
%>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – <%=isEdit?"Edit":"New"%> User</title>
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
    <a href="<%=ctx%>/admin/csr-jobs" class="nav-link"><i class="bi bi-inbox"></i>CSR Jobs</a>
    <div class="nav-sect mt-2">Administration</div>
    <a href="<%=ctx%>/admin/users/" class="nav-link active"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
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
    <span style="font-weight:600;color:#0d1b2a;font-size:1.05rem;">
      <i class="bi bi-person-gear me-2"></i><%=isEdit?"Edit User":"New User"%>
    </span>
    <a href="<%=ctx%>/admin/users/" class="btn btn-sm btn-outline-secondary"><i class="bi bi-arrow-left me-1"></i>Back</a>
  </div>
  <div class="content-area">
<% if (error != null) { %>
    <div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=error%></div>
<% } %>
    <div class="card p-4" style="max-width:580px;">
      <form method="post" action="<%=action%>">
        <div class="row g-3 mb-3">
          <div class="col-sm-6">
            <label class="form-label fw-semibold" style="font-size:.875rem;">Username *</label>
            <input type="text" name="username" class="form-control" required
                   value="<%=isEdit?esc(user.getUsername()):""%>" <%=isEdit?"readonly":""%>/>
          </div>
          <div class="col-sm-6">
            <label class="form-label fw-semibold" style="font-size:.875rem;">Display Name</label>
            <input type="text" name="displayName" class="form-control"
                   value="<%=isEdit&&user.getDisplayName()!=null?esc(user.getDisplayName()):""%>"/>
          </div>
        </div>
        <div class="mb-3">
          <label class="form-label fw-semibold" style="font-size:.875rem;">Email</label>
          <input type="email" name="email" class="form-control"
                 value="<%=isEdit&&user.getEmail()!=null?esc(user.getEmail()):""%>"/>
        </div>
<% if (!isEdit) { %>
        <div class="mb-3">
          <label class="form-label fw-semibold" style="font-size:.875rem;">Password * <small class="text-muted fw-normal">(min. 8 characters)</small></label>
          <input type="password" name="password" class="form-control" required minlength="8"/>
        </div>
<% } %>
        <div class="row g-3 mb-4">
          <div class="col-sm-6">
            <label class="form-label fw-semibold" style="font-size:.875rem;">Role</label>
            <select name="role" class="form-select">
              <option value="VIEWER" <%=isEdit&&user.getRole()==PkiUser.Role.VIEWER?"selected":""%>>VIEWER</option>
              <option value="ADMIN"  <%=isEdit&&user.getRole()==PkiUser.Role.ADMIN?"selected":""%>>ADMIN</option>
            </select>
          </div>
<% if (isEdit) { %>
          <div class="col-sm-6">
            <label class="form-label fw-semibold" style="font-size:.875rem;">Active</label>
            <select name="active" class="form-select">
              <option value="true"  <%=user.isActive()?"selected":""%>>Active</option>
              <option value="false" <%=!user.isActive()?"selected":""%>>Disabled</option>
            </select>
          </div>
<% } %>
        </div>
        <div class="d-flex gap-2">
          <button type="submit" class="btn btn-primary px-4"><i class="bi bi-save me-2"></i><%=isEdit?"Save changes":"Create user"%></button>
          <a href="<%=ctx%>/admin/users/" class="btn btn-outline-secondary">Cancel</a>
        </div>
      </form>

<% if (isEdit) { %>
      <hr class="my-4"/>
      <h6 class="fw-bold mb-3">Change Password</h6>
      <form method="post" action="<%=ctx%>/admin/users/<%=user.getId()%>/password">
        <div class="row g-3">
          <div class="col-sm-8">
            <input type="password" name="newPassword" class="form-control" required minlength="8" placeholder="New password (min. 8 chars)"/>
          </div>
          <div class="col-sm-4">
            <button type="submit" class="btn btn-outline-warning w-100"><i class="bi bi-key me-1"></i>Change</button>
          </div>
        </div>
      </form>
<% } %>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
<%! private String esc(String s){if(s==null)return"";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");} %>
