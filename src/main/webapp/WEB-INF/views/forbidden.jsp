<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>
<%@ page import="com.macmario.services.pki.entity.PkiUser" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<%
    response.setStatus(403);
    String ctx = request.getContextPath();
    Object msgObj = request.getAttribute("jakarta.servlet.error.message");
    String msg = msgObj != null ? msgObj.toString() : null;
    PkiUser me = (PkiUser) session.getAttribute("currentUser");
    boolean loggedIn = me != null;
%>
<!DOCTYPE html><html lang="en">
<head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – Forbidden</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-blue:#1b4f8a;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;}
.forbidden-card{max-width:460px;border:none;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,.10);overflow:hidden;}
.forbidden-code{font-size:4.5rem;font-weight:800;line-height:1;color:var(--pki-blue);letter-spacing:-.03em;}
.forbidden-icon{font-size:2.6rem;color:#c0392b;}
</style></head>
<body>
<div class="d-flex align-items-center justify-content-center min-vh-100 p-3">
  <div class="card forbidden-card text-center">
    <div class="p-4 p-md-5">
      <i class="bi bi-shield-lock-fill forbidden-icon mb-2 d-block"></i>
      <div class="forbidden-code">403</div>
      <h4 class="fw-bold mt-2 mb-2">Access Denied</h4>
      <p class="text-muted mb-1">You don't have permission to view this page or perform this action.</p>
      <p class="text-muted" style="font-size:.85rem;">
        This area requires <strong>administrator</strong> privileges.
        <% if(loggedIn){ %>You are signed in as <strong><%=e(me.getUsername())%></strong> (<%=e(me.getRole().name())%>).<% } %>
      </p>
      <% if(msg!=null && !msg.isBlank() && !"Forbidden".equalsIgnoreCase(msg.trim())){ %>
      <div class="alert alert-light border small text-muted mt-3 mb-0"><%=e(msg)%></div>
      <% } %>
      <div class="d-flex gap-2 justify-content-center mt-4">
        <a href="<%=ctx%>/dashboard" class="btn btn-primary"><i class="bi bi-house me-1"></i>Back to Dashboard</a>
        <% if(loggedIn){ %>
        <form method="post" action="<%=ctx%>/logout" class="d-inline">
          <button class="btn btn-outline-secondary"><i class="bi bi-box-arrow-right me-1"></i>Sign out</button>
        </form>
        <% } else { %>
        <a href="<%=ctx%>/login" class="btn btn-outline-secondary"><i class="bi bi-box-arrow-in-right me-1"></i>Log in</a>
        <% } %>
      </div>
    </div>
  </div>
</div>
</body></html>
