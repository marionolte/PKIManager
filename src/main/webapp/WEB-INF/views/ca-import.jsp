<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.CaConfig, com.macmario.services.pki.entity.PkiUser" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private String v(Object o){if(o==null)return "";return e(o.toString());} %>
<% List<CaConfig> allCas=(List<CaConfig>)request.getAttribute("allCas");
   if(allCas==null)allCas=java.util.Collections.emptyList();
   String ctx=request.getContextPath();
   String error=(String)request.getAttribute("error");
   PkiUser me=(PkiUser)session.getAttribute("currentUser");
   String fRole=v(request.getAttribute("importRoleName"));
   String fDisplay=v(request.getAttribute("importDisplayName"));
   String fType=v(request.getAttribute("importCaType")); if(fType.isEmpty())fType="ROOT";
   String fParent=v(request.getAttribute("importParentCaId"));
   String fCert=v(request.getAttribute("importCertificate")); %>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – Import CA</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>
:root{--pki-dark:#e8ecf0;--pki-teal:#00b4d8;--pki-light:#f0f4f8;}
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
.form-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);padding:2rem;}
.fsect{font-size:.7rem;text-transform:uppercase;letter-spacing:.09em;color:#888;font-weight:600;margin-bottom:.75rem;padding-bottom:.4rem;border-bottom:1px solid #eee;}
.conf-hint{font-family:monospace;font-size:.72rem;color:#aaa;}
textarea.pem{font-family:'Courier New',monospace;font-size:.75rem;}
</style></head>
<body>
<div class="sidebar">
  <div class="sidebar-brand"><div class="d-flex align-items-center gap-2 mb-1">
    <i class="bi bi-shield-lock-fill fs-4" style="color:var(--pki-teal)"></i><h5>PKI Manager</h5>
  </div><small style="color:#888;font-size:.72rem;">Internal CA</small></div>
  <nav class="flex-grow-1 py-2">
    <div class="nav-sect">Overview</div>
    <a href="<%=ctx%>/dashboard" class="nav-link"><i class="bi bi-speedometer2"></i>Dashboard</a>
    <div class="nav-sect mt-2">PKI Hierarchy</div>
    <a href="<%=ctx%>/ca" class="nav-link active"><i class="bi bi-diagram-3"></i>Certificate Authorities</a>
    <a href="<%=ctx%>/ca/create" class="nav-link"><i class="bi bi-plus-circle"></i>New CA</a>
    <a href="<%=ctx%>/ca/import" class="nav-link active"><i class="bi bi-box-arrow-in-down"></i>Import CA</a>
    <div class="nav-sect mt-2">Certificates</div>
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
    <div class="nav-sect mt-2">Requests</div>
    <a href="<%=ctx%>/admin/csr-jobs" class="nav-link"><i class="bi bi-inbox"></i>CSR Jobs</a>
    <div class="nav-sect mt-2">Administration</div>
    <a href="<%=ctx%>/admin/users/" class="nav-link"><i class="bi bi-people"></i>Users</a>
    <a href="<%=ctx%>/admin/acme" class="nav-link"><i class="bi bi-lock-fill"></i>ACME / Let's Encrypt</a>
    <a href="<%=ctx%>/api-clients/" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/admin/backup/" class="nav-link"><i class="bi bi-hdd-stack"></i>Backup &amp; Restore</a>
      <div class="nav-sect mt-2"><i class="bi bi-book me-1"></i>Documentation</div>
    <a href="<%=ctx%>/docs/certificates" class="nav-link"><i class="bi bi-file-earmark-text"></i>Certificates</a>
    <a href="<%=ctx%>/docs/scim" class="nav-link"><i class="bi bi-people"></i>SCIM</a>
    <a href="<%=ctx%>/docs/api-clients" class="nav-link"><i class="bi bi-key"></i>API Clients</a>
    <a href="<%=ctx%>/docs/acme" class="nav-link"><i class="bi bi-lock"></i>ACME</a>
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
    <span style="font-weight:600;color:#0d1b2a;"><i class="bi bi-box-arrow-in-down me-2"></i>Import Certificate Authority</span>
    <a href="<%=ctx%>/ca" class="btn btn-outline-secondary btn-sm"><i class="bi bi-arrow-left me-1"></i>Back</a>
  </div>
  <div class="content-area">
<% if(error!=null){ %><div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>
    <div class="form-card">
      <div class="alert alert-info" style="font-size:.85rem;">
        <i class="bi bi-info-circle me-1"></i>
        Import an existing Root or Sub CA by pasting its <strong>certificate</strong> and <strong>RSA private key</strong> (PEM).
        Subject, serial number, validity, key size, digest, CRL / OCSP URLs and any Name Constraints are read from the certificate.
        The private key is stored <strong>unencrypted</strong> in the database (same as generated CAs).
      </div>
      <form method="post" action="<%=ctx%>/ca/import">
        <div class="fsect">CA Role &amp; Type</div>
        <div class="row g-3 mb-4">
          <div class="col-md-4">
            <label class="form-label fw-semibold">Role Name <span class="text-danger">*</span></label>
            <input type="text" name="roleName" class="form-control" value="<%=fRole%>" placeholder="e.g. imported-root" required/>
          </div>
          <div class="col-md-4">
            <label class="form-label fw-semibold">Display Name <span class="text-danger">*</span></label>
            <input type="text" name="displayName" class="form-control" value="<%=fDisplay%>" placeholder="e.g. Imported Root CA" required/>
          </div>
          <div class="col-md-4">
            <label class="form-label fw-semibold">CA Type <span class="text-danger">*</span></label>
            <select name="caType" id="caTypeSelect" class="form-select">
              <option value="ROOT" <%="ROOT".equals(fType)?"selected":""%>>Root CA (self-signed)</option>
              <option value="INTERMEDIATE" <%="INTERMEDIATE".equals(fType)?"selected":""%>>Intermediate CA</option>
              <option value="ISSUING" <%="ISSUING".equals(fType)?"selected":""%>>Issuing CA</option>
            </select>
          </div>
        </div>

        <div id="parentCaRow" style="display:none;" class="mb-4">
          <div class="fsect">Parent CA <span class="conf-hint ms-2">optional — enables chain verification</span></div>
          <div class="col-md-6">
            <label class="form-label fw-semibold">Parent Certificate Authority</label>
            <select name="parentCaId" class="form-select">
              <option value="">— None (orphan sub CA) —</option>
<% for(CaConfig pca : allCas){ if(pca.getStatus()!=CaConfig.CaStatus.ACTIVE) continue; %>
              <option value="<%=pca.getId()%>" <%=String.valueOf(pca.getId()).equals(fParent)?"selected":""%>><%=e(pca.getDisplayName())%> (<%=pca.getCaType()%>)</option>
<% } %>
            </select>
            <div class="form-text conf-hint">If selected, the imported certificate's issuer and signature are verified against this parent.</div>
          </div>
        </div>

        <div class="fsect">Certificate &amp; Private Key (PEM)</div>
        <div class="row g-3 mb-4">
          <div class="col-md-12">
            <label class="form-label fw-semibold">CA Certificate (PEM) <span class="text-danger">*</span></label>
            <textarea name="certificatePem" class="form-control pem" rows="8" required
              placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"><%=fCert%></textarea>
            <div class="form-text conf-hint">A single certificate or a chain — the cert matching the private key is selected automatically.</div>
          </div>
          <div class="col-md-12">
            <label class="form-label fw-semibold">Private Key (PEM) <span class="text-danger">*</span></label>
            <textarea name="privateKeyPem" class="form-control pem" rows="8" required autocomplete="off"
              placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----"></textarea>
            <div class="form-text conf-hint">RSA only. PKCS#8, PKCS#1 (traditional) and encrypted keys are supported.</div>
          </div>
          <div class="col-md-6">
            <label class="form-label fw-semibold">Key Password</label>
            <input type="password" name="keyPassword" class="form-control" autocomplete="new-password" placeholder="only if the key is encrypted"/>
          </div>
        </div>

        <div class="fsect">Issuance Settings <span class="conf-hint ms-2">read from the certificate</span></div>
        <div class="alert alert-light border" style="font-size:.82rem;color:#555;">
          <i class="bi bi-magic me-1"></i>
          The <strong>digest</strong>, <strong>certificate validity (days)</strong>, <strong>CRL URL</strong> and
          <strong>OCSP URL</strong> are read automatically from the imported certificate — no need to enter them.
        </div>

        <div class="d-flex gap-2">
          <button type="submit" class="btn btn-primary"><i class="bi bi-box-arrow-in-down me-1"></i>Import CA</button>
          <a href="<%=ctx%>/ca" class="btn btn-outline-secondary">Cancel</a>
        </div>
      </form>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script>
function updateParentRow(){
  document.getElementById('parentCaRow').style.display=
    document.getElementById('caTypeSelect').value==='ROOT'?'none':'block';
}
document.getElementById('caTypeSelect').addEventListener('change', updateParentRow);
updateParentRow();
</script>
</body></html>
