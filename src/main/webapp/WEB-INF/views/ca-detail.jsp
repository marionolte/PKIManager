<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.CaConfig, com.macmario.services.pki.entity.CertificateRecord" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<%
CaConfig ca=(CaConfig)request.getAttribute("ca");
List<CaConfig> children=(List<CaConfig>)request.getAttribute("children");
List<CertificateRecord> certs=(List<CertificateRecord>)request.getAttribute("certificates");
long certCount=request.getAttribute("certCount")!=null?(long)request.getAttribute("certCount"):0;
if(children==null)children=java.util.Collections.emptyList();
if(certs==null)certs=java.util.Collections.emptyList();
String ctx=request.getContextPath();
String error=(String)request.getAttribute("error");
if(ca==null){response.sendError(404);return;}
String typeBadge=ca.getCaType()==CaConfig.CaType.ROOT?"badge-root":ca.getCaType()==CaConfig.CaType.INTERMEDIATE?"badge-inter":"badge-issuing";
String statusBadge=ca.getStatus()==CaConfig.CaStatus.DISABLED?"bg-secondary":ca.isExpired()?"bg-danger":"bg-success";
String statusLabel=ca.getStatus()==CaConfig.CaStatus.DISABLED?"Disabled":ca.isExpired()?"Expired":"Active";
%>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – <%=e(ca.getDisplayName())%></title>
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
.info-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);padding:1.5rem;}
.table-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);overflow:hidden;}
.table-card .table{margin:0;}
.table-card .table thead{background:var(--pki-dark);color:#cdd;font-size:.78rem;text-transform:uppercase;}
.table-card .table thead th{border:none;padding:.9rem 1rem;font-weight:500;}
.table-card .table tbody td{padding:.75rem 1rem;vertical-align:middle;font-size:.875rem;border-color:#f0f0f0;}
.dr{display:flex;gap:.5rem;padding:.4rem 0;border-bottom:1px solid #f5f5f5;font-size:.875rem;}
.dl{min-width:160px;color:#888;font-size:.8rem;font-weight:500;}
.cert-pem{font-family:'Courier New',monospace;font-size:.72rem;background:#0d1b2a;color:#7fdbca;border-radius:8px;padding:1rem;white-space:pre-wrap;word-break:break-all;max-height:200px;overflow-y:auto;}
.badge-valid{background:#d1fae5;color:#065f46;} .badge-revoked{background:#fee2e2;color:#991b1b;}
.badge-root{background:#dbeafe;color:#1e40af;} .badge-inter{background:#ede9fe;color:#5b21b6;} .badge-issuing{background:#d1fae5;color:#065f46;}
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
    <a href="<%=ctx%>/ca" class="nav-link active"><i class="bi bi-diagram-3"></i>Certificate Authorities</a>
    <a href="<%=ctx%>/ca/create" class="nav-link"><i class="bi bi-plus-circle"></i>New CA</a>
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
  </nav>
</div>
<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:var(--pki-dark);"><i class="bi bi-shield-lock me-2"></i><%=e(ca.getDisplayName())%></span>
    <div class="d-flex gap-2">
      <a href="<%=ctx%>/ca/<%=ca.getId()%>/cert.pem" class="btn btn-outline-secondary btn-sm"><i class="bi bi-download me-1"></i>Download PEM</a>
      <a href="<%=ctx%>/cert/issue?caId=<%=ca.getId()%>" class="btn btn-success btn-sm"><i class="bi bi-plus me-1"></i>Issue Certificate</a>
      <% if(ca.getStatus()==CaConfig.CaStatus.ACTIVE){ %>
      <form method="post" action="<%=ctx%>/ca/<%=ca.getId()%>/disable" style="display:inline;" onsubmit="return confirm('Disable this CA?')">
        <button class="btn btn-warning btn-sm"><i class="bi bi-pause-circle me-1"></i>Disable</button>
      </form>
      <% } else { %>
      <form method="post" action="<%=ctx%>/ca/<%=ca.getId()%>/enable" style="display:inline;">
        <button class="btn btn-primary btn-sm"><i class="bi bi-play-circle me-1"></i>Enable</button>
      </form>
      <% } %>
    </div>
  </div>
  <div class="content-area">
    <% if(error!=null){ %><div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>
    <div class="row g-4 mb-4">
      <div class="col-lg-7">
        <div class="info-card h-100">
          <h6 class="fw-bold mb-3"><i class="bi bi-info-circle me-2 text-primary"></i>CA Details</h6>
          <div class="dr"><span class="dl">Role Name:</span><code><%=e(ca.getRoleName())%></code></div>
          <div class="dr"><span class="dl">Type:</span><span class="badge <%=typeBadge%>"><%=ca.getCaType()%></span></div>
          <div class="dr"><span class="dl">Status:</span><span class="badge <%=statusBadge%>"><%=statusLabel%></span></div>
          <div class="dr"><span class="dl">Common Name:</span><strong><%=e(ca.getCommonName())%></strong></div>
          <div class="dr"><span class="dl">Organisation:</span><%=e(ca.getOrganization())%></div>
          <div class="dr"><span class="dl">Org Unit:</span><%=e(ca.getOrgUnit())%></div>
          <div class="dr"><span class="dl">Country / State:</span><%=e(ca.getCountry())%> / <%=e(ca.getState())%></div>
          <div class="dr"><span class="dl">Locality:</span><%=e(ca.getLocality())%></div>
          <div class="dr"><span class="dl">Email:</span><%=e(ca.getEmailAddress())%></div>
          <div class="dr"><span class="dl">Serial Number:</span><code style="font-size:.78rem;"><%=e(ca.getSerialNumber())%></code></div>
          <div class="dr"><span class="dl">Valid From:</span><%=ca.getValidFrom()!=null?ca.getValidFrom().toString().substring(0,10):""%></div>
          <div class="dr"><span class="dl">Valid Until:</span>
            <span class="<%=ca.isExpiringSoon()?"text-warning fw-bold":ca.isExpired()?"text-danger fw-bold":""%>">
              <%=ca.getValidUntil()!=null?ca.getValidUntil().toString().substring(0,10):""%>
            </span>
          </div>
          <div class="dr"><span class="dl">Key Size:</span><%=ca.getKeySize()%> bits</div>
          <div class="dr"><span class="dl">Digest:</span><%=e(ca.getDefaultMd())%></div>
          <div class="dr"><span class="dl">Default Cert Days:</span><%=ca.getDefaultDays()%></div>
          <% if(ca.getCrlUrl()!=null&&!ca.getCrlUrl().isEmpty()){ %>
          <div class="dr"><span class="dl">CRL URL:</span><a href="<%=e(ca.getCrlUrl())%>" target="_blank" style="font-size:.8rem;"><%=e(ca.getCrlUrl())%></a></div>
          <% } %>
          <% if(ca.getOcspUrl()!=null&&!ca.getOcspUrl().isEmpty()){ %>
          <div class="dr"><span class="dl">OCSP URL:</span><a href="<%=e(ca.getOcspUrl())%>" target="_blank" style="font-size:.8rem;"><%=e(ca.getOcspUrl())%></a></div>
          <% } %>
          <% if(ca.getParentCaId()!=null){ %>
          <div class="dr"><span class="dl">Parent CA:</span>
            <a href="<%=ctx%>/ca/<%=ca.getParentCaId()%>"><%=e(ca.getParentCaDisplayName())%></a>
          </div>
          <% } %>
        </div>
      </div>
      <div class="col-lg-5">
        <div class="info-card h-100">
          <h6 class="fw-bold mb-3"><i class="bi bi-file-earmark-text me-2 text-success"></i>CA Certificate PEM</h6>
          <% if(ca.getCertificatePem()!=null&&!ca.getCertificatePem().isEmpty()){ %>
          <div class="cert-pem"><%=e(ca.getCertificatePem())%></div>
          <a href="<%=ctx%>/ca/<%=ca.getId()%>/cert.pem" class="btn btn-sm btn-outline-success mt-3">
            <i class="bi bi-download me-1"></i>Download cacert.pem
          </a>
          <% } else { %>
          <div class="text-muted text-center py-4"><i class="bi bi-file-earmark-x fs-2 d-block mb-2 opacity-25"></i>No certificate yet.</div>
          <% } %>
        </div>
      </div>
    </div>

    <% if(!children.isEmpty()){ %>
    <div class="mb-4">
      <h6 class="fw-bold mb-3"><i class="bi bi-diagram-2 me-2"></i>Sub Certificate Authorities</h6>
      <div class="table-card">
        <table class="table table-hover">
          <thead><tr><th>Role</th><th>Name</th><th>Type</th><th>Status</th><th></th></tr></thead>
          <tbody>
          <% for(CaConfig ch:children){ %>
          <tr>
            <td><code style="font-size:.8rem;"><%=e(ch.getRoleName())%></code></td>
            <td><%=e(ch.getDisplayName())%></td>
            <td><span class="badge <%=ch.getCaType()==CaConfig.CaType.INTERMEDIATE?"badge-inter":"badge-issuing"%> rounded-pill" style="font-size:.68rem;"><%=ch.getCaType()%></span></td>
            <td><span class="badge <%=ch.getStatus()==CaConfig.CaStatus.ACTIVE?"bg-success":"bg-secondary"%>"><%=ch.getStatus()%></span></td>
            <td><a href="<%=ctx%>/ca/<%=ch.getId()%>" class="btn btn-sm btn-outline-primary py-0 px-2"><i class="bi bi-eye"></i></a></td>
          </tr>
          <% } %>
          </tbody>
        </table>
      </div>
    </div>
    <% } %>

    <div>
      <div class="d-flex align-items-center justify-content-between mb-3">
        <h6 class="fw-bold mb-0"><i class="bi bi-file-earmark-lock2 me-2 text-success"></i>Issued Certificates (<%=certCount%>)</h6>
        <a href="<%=ctx%>/cert/issue?caId=<%=ca.getId()%>" class="btn btn-sm btn-success"><i class="bi bi-plus me-1"></i>Issue New</a>
      </div>
      <% if(certs.isEmpty()){ %>
      <div class="text-center text-muted py-4" style="background:#fff;border-radius:12px;">
        <i class="bi bi-file-earmark-lock2 fs-2 d-block mb-2 opacity-25"></i>No certificates issued by this CA yet.
      </div>
      <% } else { %>
      <div class="table-card">
        <table class="table table-hover">
          <thead><tr><th>Common Name</th><th>Type</th><th>Serial</th><th>Valid Until</th><th>Status</th><th></th></tr></thead>
          <tbody>
          <% for(CertificateRecord cert:certs){
             String stBadge=cert.getCertStatus()==CertificateRecord.CertStatus.VALID?"badge-valid":"badge-revoked"; %>
          <tr>
            <td class="fw-semibold"><a href="<%=ctx%>/cert/<%=cert.getId()%>" class="text-decoration-none"><%=e(cert.getCommonName())%></a></td>
            <td><span class="badge bg-secondary bg-opacity-75 text-white" style="font-size:.68rem;"><%=cert.getCertType()%></span></td>
            <td><code style="font-size:.72rem;"><%=e(cert.getSerialNumber())%></code></td>
            <td style="font-size:.82rem;" class="<%=cert.isExpiringSoon()?"text-warning fw-bold":cert.isExpired()?"text-danger":""%>">
              <%=cert.getValidUntil()!=null?cert.getValidUntil().toString().substring(0,10):""%>
            </td>
            <td><span class="badge <%=stBadge%> rounded-pill" style="font-size:.68rem;"><%=cert.getCertStatus()%></span></td>
            <td><a href="<%=ctx%>/cert/<%=cert.getId()%>" class="btn btn-sm btn-outline-primary py-0 px-2"><i class="bi bi-eye"></i></a></td>
          </tr>
          <% } %>
          </tbody>
        </table>
      </div>
      <% } %>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
