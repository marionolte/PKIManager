<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, com.macmario.services.pki.entity.CaConfig" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<% List<CaConfig> allCas=(List<CaConfig>)request.getAttribute("allCas");
   if(allCas==null)allCas=java.util.Collections.emptyList();
   String ctx=request.getContextPath();
   String error=(String)request.getAttribute("error");
   String preselCaId=request.getParameter("caId"); %>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – Issue Certificate</title>
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
    <a href="<%=ctx%>/cert" class="nav-link"><i class="bi bi-file-earmark-lock2"></i>All Certificates</a>
    <a href="<%=ctx%>/cert/issue" class="nav-link active"><i class="bi bi-plus-circle-dotted"></i>Issue Certificate</a>
  </nav>
</div>
<div class="main-content">
  <div class="topbar">
    <span style="font-weight:600;color:var(--pki-dark);"><i class="bi bi-file-earmark-plus me-2"></i>Issue Certificate</span>
    <a href="<%=ctx%>/cert" class="btn btn-outline-secondary btn-sm"><i class="bi bi-arrow-left me-1"></i>Back</a>
  </div>
  <div class="content-area">
    <% if(error!=null){ %><div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-2"></i><%=e(error)%></div><% } %>

    <ul class="nav nav-tabs" id="issueTabs">
      <li class="nav-item"><button class="nav-link active" data-bs-toggle="tab" data-bs-target="#gen-panel" type="button"><i class="bi bi-key me-1"></i>Generate Key + Certificate</button></li>
      <li class="nav-item"><button class="nav-link" data-bs-toggle="tab" data-bs-target="#csr-panel" type="button"><i class="bi bi-file-earmark-arrow-up me-1"></i>Sign External CSR</button></li>
    </ul>

    <div class="tab-content">
      <!-- Tab 1: Generate -->
      <div class="tab-pane fade show active" id="gen-panel">
        <div class="form-card" style="border-radius:0 12px 12px 12px;">
          <form method="post" action="<%=ctx%>/cert/issue">
            <input type="hidden" name="csrPem" value=""/>
            <div class="fsect">Issuing CA</div>
            <div class="row g-3 mb-4">
              <div class="col-md-5">
                <label class="form-label fw-semibold">Certificate Authority <span class="text-danger">*</span></label>
                <select name="caId" class="form-select" required>
                  <option value="">— Select CA —</option>
                  <% for(CaConfig ca:allCas){ boolean presel=preselCaId!=null&&preselCaId.equals(String.valueOf(ca.getId())); %>
                  <option value="<%=ca.getId()%>" <%=presel?"selected":""%> <%=ca.getStatus()!=CaConfig.CaStatus.ACTIVE?"disabled":""%>>
                    <%=e(ca.getDisplayName())%> (<%=ca.getCaType()%>)<%=ca.getStatus()!=CaConfig.CaStatus.ACTIVE?" [DISABLED]":""%>
                  </option>
                  <% } %>
                </select>
              </div>
              <div class="col-md-4">
                <label class="form-label fw-semibold">Certificate Type</label>
                <select name="certType" class="form-select">
                  <option value="SERVER">Server (TLS)</option>
                  <option value="CLIENT">Client (mTLS)</option>
                  <option value="CODE_SIGNING">Code Signing</option>
                  <option value="EMAIL">Email (S/MIME)</option>
                </select>
              </div>
              <div class="col-md-3">
                <label class="form-label fw-semibold">Key Size</label>
                <select name="keySize" class="form-select">
                  <option value="2048" selected>2048 bit</option>
                  <option value="4096">4096 bit</option>
                </select>
              </div>
            </div>

            <div class="fsect">Subject DN</div>
            <div class="row g-3 mb-4">
              <div class="col-md-7"><label class="form-label fw-semibold">Common Name (CN) <span class="text-danger">*</span></label>
                <input type="text" name="commonName" class="form-control" placeholder="e.g. webserver.example.local" required/></div>
              <div class="col-md-2"><label class="form-label fw-semibold">Country (C)</label>
                <input type="text" name="country" class="form-control" maxlength="3" placeholder="DE" value="DE"/></div>
              <div class="col-md-3"><label class="form-label fw-semibold">State (ST)</label>
                <input type="text" name="state" class="form-control" placeholder="NRW"/></div>
              <div class="col-md-4"><label class="form-label fw-semibold">Locality (L)</label>
                <input type="text" name="locality" class="form-control" placeholder="Köln"/></div>
              <div class="col-md-4"><label class="form-label fw-semibold">Organisation (O)</label>
                <input type="text" name="organization" class="form-control" placeholder="example.local"/></div>
              <div class="col-md-4"><label class="form-label fw-semibold">Org Unit (OU)</label>
                <input type="text" name="orgUnit" class="form-control" placeholder="IT"/></div>
              <div class="col-md-6"><label class="form-label fw-semibold">Email</label>
                <input type="email" name="emailAddress" class="form-control" placeholder="admin@example.local"/></div>
            </div>

            <div class="fsect">Subject Alternative Names</div>
            <div class="row g-3 mb-4">
              <div class="col-md-8"><label class="form-label fw-semibold">DNS Names (comma-separated)</label>
                <input type="text" name="sanDns" class="form-control" placeholder="webserver.example.local, www.example.local"/>
                <div class="form-text">Multiple hostnames separated by comma</div></div>
              <div class="col-md-4"><label class="form-label fw-semibold">IP Addresses (comma-separated)</label>
                <input type="text" name="sanIp" class="form-control" placeholder="192.168.1.10"/></div>
            </div>

            <div class="fsect">Metadata</div>
            <div class="row g-3 mb-4">
              <div class="col-md-6"><label class="form-label fw-semibold">Requester</label>
                <input type="text" name="requester" class="form-control" placeholder="Name / department"/></div>
              <div class="col-md-6"><label class="form-label fw-semibold">Notes</label>
                <input type="text" name="notes" class="form-control" placeholder="Purpose / remarks"/></div>
            </div>
            <button type="submit" class="btn btn-success"><i class="bi bi-patch-check me-1"></i>Generate &amp; Issue Certificate</button>
          </form>
        </div>
      </div>

      <!-- Tab 2: Sign CSR -->
      <div class="tab-pane fade" id="csr-panel">
        <div class="form-card" style="border-radius:0 12px 12px 12px;">
          <form method="post" action="<%=ctx%>/cert/issue">
            <div class="fsect">Issuing CA</div>
            <div class="row g-3 mb-4">
              <div class="col-md-5">
                <label class="form-label fw-semibold">Certificate Authority <span class="text-danger">*</span></label>
                <select name="caId" class="form-select" required>
                  <option value="">— Select CA —</option>
                  <% for(CaConfig ca:allCas){ %>
                  <option value="<%=ca.getId()%>" <%=ca.getStatus()!=CaConfig.CaStatus.ACTIVE?"disabled":""%>><%=e(ca.getDisplayName())%> (<%=ca.getCaType()%>)</option>
                  <% } %>
                </select>
              </div>
              <div class="col-md-4">
                <label class="form-label fw-semibold">Certificate Type</label>
                <select name="certType" class="form-select">
                  <option value="SERVER">Server (TLS)</option>
                  <option value="CLIENT">Client (mTLS)</option>
                  <option value="CODE_SIGNING">Code Signing</option>
                  <option value="EMAIL">Email (S/MIME)</option>
                </select>
              </div>
            </div>
            <div class="fsect">PEM-encoded CSR</div>
            <div class="mb-4">
              <textarea name="csrPem" rows="10" class="form-control" style="font-family:monospace;font-size:.8rem;"
                placeholder="-----BEGIN CERTIFICATE REQUEST-----&#10;...&#10;-----END CERTIFICATE REQUEST-----" required></textarea>
            </div>
            <!-- Required hidden fields for template -->
            <input type="hidden" name="commonName" value="from-csr"/>
            <input type="hidden" name="country" value=""/><input type="hidden" name="state" value=""/>
            <input type="hidden" name="locality" value=""/><input type="hidden" name="organization" value=""/>
            <input type="hidden" name="orgUnit" value=""/><input type="hidden" name="emailAddress" value=""/>
            <input type="hidden" name="sanDns" value=""/><input type="hidden" name="sanIp" value=""/>
            <input type="hidden" name="keySize" value="2048"/>
            <div class="row g-3 mb-4">
              <div class="col-md-6"><label class="form-label fw-semibold">Requester</label>
                <input type="text" name="requester" class="form-control"/></div>
              <div class="col-md-6"><label class="form-label fw-semibold">Notes</label>
                <input type="text" name="notes" class="form-control"/></div>
            </div>
            <button type="submit" class="btn btn-primary"><i class="bi bi-file-earmark-check me-1"></i>Sign CSR</button>
          </form>
        </div>
      </div>
    </div><!-- tab-content -->
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
