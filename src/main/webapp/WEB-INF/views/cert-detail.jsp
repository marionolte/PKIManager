<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="com.macmario.services.pki.entity.CertificateRecord, com.macmario.services.pki.entity.RevokedCertificate" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<% CertificateRecord cert=(CertificateRecord)request.getAttribute("cert");
   RevokedCertificate.RevocationReason[] reasons=(RevokedCertificate.RevocationReason[])request.getAttribute("revocationReasons");
   String ctx=request.getContextPath();
   String error=(String)request.getAttribute("error");
   if(cert==null){response.sendError(404);return;}
   String stBadge=cert.getCertStatus()==CertificateRecord.CertStatus.VALID?"badge-valid":"badge-revoked"; %>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – <%=e(cert.getCommonName())%></title>
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
.dr{display:flex;gap:.5rem;padding:.4rem 0;border-bottom:1px solid #f5f5f5;font-size:.875rem;}
.dl{min-width:170px;color:#888;font-size:.8rem;font-weight:500;}
.cert-pem{font-family:'Courier New',monospace;font-size:.72rem;background:#0d1b2a;color:#7fdbca;border-radius:8px;padding:1rem;white-space:pre-wrap;word-break:break-all;max-height:220px;overflow-y:auto;}
.badge-valid{background:#d1fae5;color:#065f46;} .badge-revoked{background:#fee2e2;color:#991b1b;}
.fingerprint{font-family:monospace;font-size:.68rem;word-break:break-all;color:#555;}
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
    <a href="<%=ctx%>/ca/create" class="nav-link"><i class="bi bi-plus-circle"></i>New CA</a>
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link active"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
  </nav>
</div>
<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:var(--pki-dark);"><i class="bi bi-file-earmark-lock me-2"></i><%=e(cert.getCommonName())%></span>
    <div class="d-flex gap-2">
      <a href="<%=ctx%>/cert/<%=cert.getId()%>/download.pem" class="btn btn-outline-secondary btn-sm"><i class="bi bi-download me-1"></i>Download PEM</a>
      <% if(cert.getCertStatus()==CertificateRecord.CertStatus.VALID){ %>
      <button class="btn btn-danger btn-sm" data-bs-toggle="modal" data-bs-target="#revokeModal"><i class="bi bi-x-circle me-1"></i>Revoke</button>
      <% } %>
    </div>
  </div>
  <div class="content-area">
    <% if(error!=null){ %><div class="alert alert-danger mb-4"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>
    <% if(cert.getCertStatus()==CertificateRecord.CertStatus.REVOKED){ %>
    <div class="alert alert-danger d-flex align-items-center gap-2 mb-4">
      <i class="bi bi-x-circle-fill fs-5"></i>
      <div><strong>Certificate Revoked.</strong> This certificate has been revoked and should no longer be trusted.</div>
    </div>
    <% } else if(cert.isExpiringSoon()){ %>
    <div class="alert alert-warning d-flex align-items-center gap-2 mb-4">
      <i class="bi bi-exclamation-triangle-fill"></i>
      <div><strong>Expiring Soon.</strong> This certificate expires within 30 days.</div>
    </div>
    <% } %>

    <div class="row g-4">
      <div class="col-lg-6">
        <div class="info-card h-100">
          <h6 class="fw-bold mb-3"><i class="bi bi-info-circle me-2 text-primary"></i>Certificate Details</h6>
          <div class="dr"><span class="dl">Status:</span><span class="badge <%=stBadge%>"><%=cert.getCertStatus()%></span></div>
          <div class="dr"><span class="dl">Type:</span><span class="badge bg-secondary text-white"><%=cert.getCertType()%></span></div>
          <div class="dr"><span class="dl">Common Name (CN):</span><strong><%=e(cert.getCommonName())%></strong></div>
          <div class="dr"><span class="dl">Organisation:</span><%=e(cert.getOrganization())%></div>
          <div class="dr"><span class="dl">Org Unit:</span><%=e(cert.getOrgUnit())%></div>
          <div class="dr"><span class="dl">Country / State:</span><%=e(cert.getCountry())%> / <%=e(cert.getState())%></div>
          <div class="dr"><span class="dl">Locality:</span><%=e(cert.getLocality())%></div>
          <div class="dr"><span class="dl">Email:</span><%=e(cert.getEmailAddress())%></div>
          <div class="dr"><span class="dl">Serial Number:</span><code style="font-size:.78rem;"><%=e(cert.getSerialNumber())%></code></div>
          <div class="dr"><span class="dl">Valid From:</span><%=cert.getValidFrom()!=null?cert.getValidFrom().toString().substring(0,10):""%></div>
          <div class="dr"><span class="dl">Valid Until:</span>
            <span class="<%=cert.isExpiringSoon()?"text-warning fw-bold":cert.isExpired()?"text-danger fw-bold":""%>">
              <%=cert.getValidUntil()!=null?cert.getValidUntil().toString().substring(0,10):""%>
            </span>
          </div>
          <div class="dr"><span class="dl">Key Size:</span><%=cert.getKeySize()%> bits</div>
          <div class="dr"><span class="dl">Signature Algorithm:</span><%=e(cert.getSignatureAlgorithm())%></div>
          <% if(cert.getSanDns()!=null&&!cert.getSanDns().isEmpty()){ %>
          <div class="dr"><span class="dl">DNS SANs:</span><%=e(cert.getSanDns())%></div>
          <% } %>
          <% if(cert.getSanIp()!=null&&!cert.getSanIp().isEmpty()){ %>
          <div class="dr"><span class="dl">IP SANs:</span><%=e(cert.getSanIp())%></div>
          <% } %>
          <div class="dr"><span class="dl">Issued At:</span><%=cert.getIssuedAt()!=null?cert.getIssuedAt().toString().substring(0,16):""%></div>
          <% if(cert.getRequester()!=null){ %>
          <div class="dr"><span class="dl">Requester:</span><%=e(cert.getRequester())%></div>
          <% } %>
          <% if(cert.getNotes()!=null){ %>
          <div class="dr"><span class="dl">Notes:</span><%=e(cert.getNotes())%></div>
          <% } %>
          <div class="dr"><span class="dl">Issuing CA:</span>
            <a href="<%=ctx%>/ca/<%=cert.getIssuingCaId()%>"><%=e(cert.getIssuingCaDisplayName())%></a>
          </div>
          <% if(cert.getFingerprintSha256()!=null){ %>
          <div class="dr flex-column">
            <span class="dl mb-1">SHA-256 Fingerprint:</span>
            <span class="fingerprint"><%=e(cert.getFingerprintSha256())%></span>
          </div>
          <% } %>
        </div>
      </div>
      <div class="col-lg-6">
        <div class="info-card mb-4">
          <h6 class="fw-bold mb-3"><i class="bi bi-file-earmark-text me-2 text-success"></i>Certificate PEM</h6>
          <% if(cert.getCertificatePem()!=null){ %>
          <div class="cert-pem"><%=e(cert.getCertificatePem())%></div>
          <a href="<%=ctx%>/cert/<%=cert.getId()%>/download.pem" class="btn btn-sm btn-outline-success mt-2"><i class="bi bi-download me-1"></i>Download cert.pem</a>
          <% } %>
        </div>
        <% if(cert.getPrivateKeyPem()!=null&&!cert.getPrivateKeyPem().isEmpty()){ %>
        <div class="info-card">
          <h6 class="fw-bold mb-3"><i class="bi bi-key me-2 text-warning"></i>Private Key</h6>
          <div class="alert alert-warning py-2" style="font-size:.78rem;"><i class="bi bi-exclamation-triangle me-1"></i>Store securely. Delete from DB after download.</div>
          <div class="cert-pem"><%=e(cert.getPrivateKeyPem())%></div>
        </div>
        <% } %>
      </div>
    </div>
  </div>
</div>

<!-- Revoke Modal -->
<div class="modal fade" id="revokeModal" tabindex="-1">
  <div class="modal-dialog">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title"><i class="bi bi-x-circle me-2 text-danger"></i>Revoke Certificate</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
      </div>
      <form method="post" action="<%=ctx%>/cert/<%=cert.getId()%>/revoke">
        <div class="modal-body">
          <p>Revoke certificate for <strong><%=e(cert.getCommonName())%></strong>? This cannot be undone.</p>
          <div class="mb-3">
            <label class="form-label fw-semibold">Revocation Reason</label>
            <select name="reason" class="form-select">
              <% if(reasons!=null){for(RevokedCertificate.RevocationReason r:reasons){ %>
              <option value="<%=r.name()%>"><%=r.name()%></option>
              <% }} %>
            </select>
          </div>
          <div class="mb-3">
            <label class="form-label fw-semibold">Comment</label>
            <textarea name="comment" rows="3" class="form-control" placeholder="Reason for revocation…"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
          <button type="submit" class="btn btn-danger"><i class="bi bi-x-circle me-1"></i>Revoke Certificate</button>
        </div>
      </form>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
