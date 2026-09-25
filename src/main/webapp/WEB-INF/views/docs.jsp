<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.macmario.services.pki.entity.PkiUser" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<% String ctx=request.getContextPath();
   PkiUser me=(PkiUser)session.getAttribute("currentUser");
   boolean isAdmin=me!=null&&me.isAdmin();
   String topic=(String)request.getAttribute("topic"); if(topic==null)topic="overview";
   String docTitle=(String)request.getAttribute("docTitle"); if(docTitle==null)docTitle="Documentation";
   String contentPage=(String)request.getAttribute("contentPage");
   String scheme=request.getScheme(); int port=request.getServerPort();
   String portPart=(("http".equals(scheme)&&port==80)||("https".equals(scheme)&&port==443))?"":(":"+port);
   request.setAttribute("appBase", scheme+"://"+request.getServerName()+portPart+ctx);
%>
<!DOCTYPE html><html lang="en">
<head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<title>PKI Manager – <%=e(docTitle)%></title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-dark:#e8ecf0;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
body{background:var(--pki-light);font-family:'Segoe UI',sans-serif;}
.sidebar{position:fixed;top:0;left:0;width:240px;height:100vh;background:var(--pki-dark);border-right:1px solid #d1d9e0;display:flex;flex-direction:column;z-index:100;overflow-y:auto;}
.sidebar-brand{padding:1.5rem 1.2rem;border-bottom:1px solid rgba(0,0,0,.08);}
.sidebar-brand h5{color:#1b4f8a;font-weight:700;margin:0;font-size:.95rem;}
.nav-sect{padding:.5rem 1rem .2rem;font-size:.68rem;text-transform:uppercase;letter-spacing:.08em;color:#64748b;}
.sidebar .nav-link{color:#334155;padding:.5rem 1.2rem;font-size:.875rem;border-radius:0;}
.sidebar .nav-link:hover,.sidebar .nav-link.active{background:rgba(27,79,138,.1);color:#1b4f8a;}
.sidebar .nav-link.sub{padding-left:2.4rem;font-size:.83rem;}
.sidebar .nav-link i{width:20px;margin-right:8px;}
.main-content{margin-left:240px;min-height:100vh;}
.topbar{background:#fff;border-bottom:1px solid #dde4ee;padding:.75rem 2rem;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:50;}
.content-area{padding:2rem;max-width:960px;}
.doc-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);padding:2rem 2.25rem;}
.doc-card h1{font-size:1.6rem;font-weight:700;color:#0d1b2a;}
.doc-card h2{font-size:1.15rem;font-weight:700;color:#1b4f8a;margin-top:1.8rem;padding-bottom:.3rem;border-bottom:1px solid #eef2f6;}
.doc-card h3{font-size:.98rem;font-weight:600;color:#334155;margin-top:1.2rem;}
.doc-card p,.doc-card li{font-size:.9rem;color:#374151;line-height:1.6;}
.doc-card code{background:#f1f5f9;color:#b91c1c;padding:.1rem .35rem;border-radius:4px;font-size:.82rem;}
.doc-card pre{background:#0f172a;color:#e2e8f0;border-radius:8px;padding:1rem 1.2rem;overflow-x:auto;font-size:.8rem;line-height:1.5;}
.doc-card pre code{background:none;color:inherit;padding:0;}
.doc-card table{width:100%;font-size:.85rem;border-collapse:collapse;margin:.5rem 0;}
.doc-card th{background:#dde4ee;text-align:left;padding:.5rem .7rem;font-weight:600;}
.doc-card td{padding:.5rem .7rem;border-top:1px solid #eef2f6;vertical-align:top;}
.doc-note{background:#eff6ff;border-left:3px solid #1b4f8a;padding:.7rem 1rem;border-radius:6px;font-size:.85rem;margin:1rem 0;}
.doc-warn{background:#fef2f2;border-left:3px solid #c0392b;padding:.7rem 1rem;border-radius:6px;font-size:.85rem;margin:1rem 0;}
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
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <div class="nav-sect mt-2">Integration</div>
    <a href="<%=ctx%>/api-clients/" class="nav-link"><i class="bi bi-key"></i>My API Clients</a>
    <div class="nav-sect mt-2"><i class="bi bi-book me-1"></i>Documentation</div>
    <a href="<%=ctx%>/docs/certificates" class="nav-link sub <%="certificates".equals(topic)?"active":""%>"><i class="bi bi-file-earmark-text"></i>Certificates</a>
    <a href="<%=ctx%>/docs/scim" class="nav-link sub <%="scim".equals(topic)?"active":""%>"><i class="bi bi-people"></i>SCIM 2.0</a>
    <a href="<%=ctx%>/docs/api-clients" class="nav-link sub <%="api-clients".equals(topic)?"active":""%>"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/docs/acme" class="nav-link sub <%="acme".equals(topic)?"active":""%>"><i class="bi bi-lock"></i>ACME</a>
  </nav>
  <div class="p-3" style="border-top:1px solid rgba(0,0,0,.08);font-size:.72rem;color:#64748b;">
    <i class="bi bi-person-circle me-1"></i><%=me!=null?e(me.getDisplayName()):""%>
    <form method="post" action="<%=ctx%>/logout" class="d-inline ms-2">
      <button class="btn btn-link btn-sm p-0 text-danger" style="font-size:.72rem;"><i class="bi bi-box-arrow-right"></i> Logout</button>
    </form>
  </div>
</div>
<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:#0d1b2a;"><i class="bi bi-book me-2"></i>Documentation<% if(!"overview".equals(topic)){ %> <span class="text-muted">/ <%=e(docTitle)%></span><% } %></span>
    <a href="<%=ctx%>/dashboard" class="btn btn-outline-secondary btn-sm"><i class="bi bi-arrow-left me-1"></i>Back to app</a>
  </div>
  <div class="content-area">
    <div class="doc-card">
<% if(contentPage!=null){ %>
      <jsp:include page="<%= contentPage %>"/>
<% } %>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
