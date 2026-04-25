<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.CaConfig" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} 
    private String v(String s){if(s==null)return "";return e(s);} %>
<% List<CaConfig> allCas=(List<CaConfig>)request.getAttribute("allCas");
   if(allCas==null)allCas=java.util.Collections.emptyList();
   String ctx=request.getContextPath();
   String error=(String)request.getAttribute("error"); %>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – New CA</title>
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
.form-card{background:#fff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.06);padding:2rem;}
.fsect{font-size:.7rem;text-transform:uppercase;letter-spacing:.09em;color:#888;font-weight:600;margin-bottom:.75rem;padding-bottom:.4rem;border-bottom:1px solid #eee;}
.conf-hint{font-family:monospace;font-size:.72rem;color:#aaa;}
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
    <span style="font-weight:600;color:var(--pki-dark);"><i class="bi bi-diagram-3 me-2"></i>New Certificate Authority</span>
    <a href="<%=ctx%>/ca" class="btn btn-outline-secondary btn-sm"><i class="bi bi-arrow-left me-1"></i>Back</a>
  </div>
  <div class="content-area">
<% if(error!=null){ %><div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>
    <div class="form-card">
      <form method="post" action="<%=ctx%>/ca/create">
        <div class="fsect">CA Role &amp; Type <span class="conf-hint ms-2">[global] role= in pki.conf</span></div>
        <div class="row g-3 mb-4">
          <div class="col-md-4">
            <label class="form-label fw-semibold">Role Name <span class="text-danger">*</span></label>
            <input type="text" name="roleName" class="form-control" placeholder="e.g. master, subRoot1" required/>
            <div class="form-text conf-hint">Matches role= in pki.conf</div>
          </div>
          <div class="col-md-4">
            <label class="form-label fw-semibold">Display Name <span class="text-danger">*</span></label>
            <input type="text" name="displayName" class="form-control" placeholder="e.g. Root CA" required/>
          </div>
          <div class="col-md-4">
            <label class="form-label fw-semibold">CA Type <span class="text-danger">*</span></label>
            <select name="caType" id="caTypeSelect" class="form-select">
              <option value="ROOT">Root CA (self-signed)</option>
              <option value="INTERMEDIATE">Intermediate CA</option>
              <option value="ISSUING">Issuing CA (signs end-entity)</option>
            </select>
          </div>
        </div>

        <div id="parentCaRow" style="display:none;" class="mb-4">
          <div class="fsect">Parent CA</div>
          <div class="col-md-6">
            <label class="form-label fw-semibold">Parent Certificate Authority</label>
            <select name="parentCaId" class="form-select">
              <option value="">— Select Parent CA —</option>
<% for(CaConfig pca : allCas){ %>
              <option value="<%=pca.getId()%>"><%=e(pca.getDisplayName())%> (<%=pca.getCaType()%>)</option>
<% } %>
            </select>
          </div>
        </div>

        <div class="fsect">Subject Distinguished Name <span class="conf-hint ms-2">C, ST, L, O, OU, CN, emailAddress</span></div>
        <div class="row g-3 mb-4">
          <div class="col-md-2"><label class="form-label fw-semibold">Country (C)</label>
            <input type="text" name="country" class="form-control" maxlength="3" placeholder="DE" value="DE"/></div>
          <div class="col-md-4"><label class="form-label fw-semibold">State (ST)</label>
            <input type="text" name="state" class="form-control" placeholder="example.local"/></div>
          <div class="col-md-6"><label class="form-label fw-semibold">Locality (L)</label>
            <input type="text" name="locality" class="form-control" placeholder="rz.example.local"/></div>
          <div class="col-md-6"><label class="form-label fw-semibold">Organisation (O)</label>
            <input type="text" name="organization" class="form-control" placeholder="example.local"/></div>
          <div class="col-md-6"><label class="form-label fw-semibold">Org Unit (OU)</label>
            <input type="text" name="orgUnit" class="form-control" placeholder="intern Certificate Authority"/></div>
          <div class="col-md-8"><label class="form-label fw-semibold">Common Name (CN) <span class="text-danger">*</span></label>
            <input type="text" name="commonName" class="form-control" placeholder="e.g. INT CA Root" required/></div>
          <div class="col-md-4"><label class="form-label fw-semibold">Email Address</label>
            <input type="email" name="emailAddress" class="form-control" placeholder="pki-master@example.local"/></div>
        </div>

        <div class="fsect">Cryptographic Settings <span class="conf-hint ms-2">default_days, default_md</span></div>
        <div class="row g-3 mb-4">
          <div class="col-md-3"><label class="form-label fw-semibold">Key Size (bits)</label>
            <select name="keySize" class="form-select"><option value="2048">2048</option><option value="4096" selected>4096</option></select></div>
          <div class="col-md-3"><label class="form-label fw-semibold">Digest (default_md)</label>
            <select name="defaultMd" class="form-select"><option value="sha256" selected>sha256</option><option value="sha384">sha384</option><option value="sha512">sha512</option></select></div>
          <div class="col-md-3"><label class="form-label fw-semibold">Validity (days)</label>
            <input type="number" name="defaultDays" class="form-control" value="730" min="1" max="7300"/>
            <div class="form-text conf-hint">default_days =</div></div>
        </div>

        <div class="fsect">Distribution URLs <span class="conf-hint ms-2">crlUrl, ocspUrl</span></div>
        <div class="row g-3 mb-4">
          <div class="col-md-6"><label class="form-label fw-semibold">CRL URL</label>
            <input type="url" name="crlUrl" class="form-control" placeholder="https://crl.example.local/crl"/>
            <div class="form-text conf-hint">crlUrl =</div></div>
          <div class="col-md-6"><label class="form-label fw-semibold">OCSP URL</label>
            <input type="url" name="ocspUrl" class="form-control" placeholder="https://ocsp.example.local/ocsp"/>
            <div class="form-text conf-hint">ocspUrl =</div></div>
        </div>

        <div class="d-flex gap-2">
          <button type="submit" class="btn btn-primary"><i class="bi bi-shield-plus me-1"></i>Generate &amp; Save CA</button>
          <a href="<%=ctx%>/ca" class="btn btn-outline-secondary">Cancel</a>
        </div>
        <div class="alert alert-info mt-3" style="font-size:.8rem;">
          <i class="bi bi-info-circle me-1"></i>
          Clicking <strong>Generate &amp; Save CA</strong> creates an RSA key pair and signs the CA certificate
          immediately using Bouncy Castle. Private key is stored in the H2 database.
        </div>
      </form>
    </div>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script>
document.getElementById('caTypeSelect').addEventListener('change',function(){
  document.getElementById('parentCaRow').style.display=this.value==='ROOT'?'none':'';
});
</script>
</body></html>
