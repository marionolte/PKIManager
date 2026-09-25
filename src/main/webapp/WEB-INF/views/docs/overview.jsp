<%@ page contentType="text/html;charset=UTF-8" %>
<% String ctx=request.getContextPath(); %>
<h1><i class="bi bi-book me-2"></i>PKI Manager Documentation</h1>
<p>Guides for the main features of PKI Manager. Pick a topic from the <strong>Documentation</strong> menu
   in the sidebar, or from the cards below.</p>

<div class="row g-3 mt-2">
  <div class="col-md-6">
    <a href="<%=ctx%>/docs/certificates" class="text-decoration-none">
      <div class="border rounded p-3 h-100">
        <h3 class="mt-0"><i class="bi bi-file-earmark-lock2 me-2 text-success"></i>Certificates</h3>
        <p class="mb-0 text-muted">Issue, download and revoke certificates; certificate types and SANs.</p>
      </div></a>
  </div>
  <div class="col-md-6">
    <a href="<%=ctx%>/docs/api-clients" class="text-decoration-none">
      <div class="border rounded p-3 h-100">
        <h3 class="mt-0"><i class="bi bi-key me-2 text-primary"></i>API Clients</h3>
        <p class="mb-0 text-muted">Request, approve and use REST API keys; curl examples.</p>
      </div></a>
  </div>
  <div class="col-md-6">
    <a href="<%=ctx%>/docs/scim" class="text-decoration-none">
      <div class="border rounded p-3 h-100">
        <h3 class="mt-0"><i class="bi bi-people me-2 text-info"></i>SCIM 2.0</h3>
        <p class="mb-0 text-muted">Automated provisioning of users, roles, certificates and API clients.</p>
      </div></a>
  </div>
  <div class="col-md-6">
    <a href="<%=ctx%>/docs/acme" class="text-decoration-none">
      <div class="border rounded p-3 h-100">
        <h3 class="mt-0"><i class="bi bi-lock me-2 text-warning"></i>ACME / Let's Encrypt</h3>
        <p class="mb-0 text-muted">Obtain publicly-trusted TLS certificates via ACME v2.</p>
      </div></a>
  </div>
</div>

<div class="doc-note mt-4"><i class="bi bi-info-circle me-1"></i>
  These pages describe this deployment. Machine-facing base URL:
  <code><%= request.getAttribute("appBase") %></code>.
</div>
