<%@ page contentType="text/html;charset=UTF-8" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<% String ctx=request.getContextPath(); String base=(String)request.getAttribute("appBase"); %>
<h1><i class="bi bi-key me-2 text-primary"></i>API Clients</h1>
<p>API clients let external applications call the PKI REST API with a static key instead of a login session.
   Each client is <strong>owned</strong> by the user who created it and must be <strong>approved by an
   administrator</strong> before its key works.</p>

<h2>Request &amp; approval workflow</h2>
<ol>
  <li>Any signed-in user requests a client at <a href="<%=ctx%>/api-clients/">My API Clients</a>. It starts
      <strong>Pending</strong> and inactive.</li>
  <li>An administrator approves it under <em>Administration → API Clients (admin)</em>. Approval activates it;
      rejection disables it permanently.</li>
  <li>The owner then reveals/rotates the key, enables/disables and deletes <strong>their own</strong> clients.</li>
</ol>
<div class="doc-note"><i class="bi bi-info-circle me-1"></i>
  A key authenticates only when the client is <strong>active, approved, and its owner is still active</strong>.
  Deactivating or deleting the owner immediately disables the key.</div>

<h2>Authentication</h2>
<p>Send the key in the <code>X-API-Key</code> header. Base path: <code><%=e(base)%>/api/v1/</code></p>

<h2>Endpoints</h2>
<table>
  <thead><tr><th>Method</th><th>Path</th><th>Description</th></tr></thead>
  <tbody>
    <tr><td>GET</td><td><code>/api/v1/cas</code></td><td>List active issuing CAs</td></tr>
    <tr><td>GET</td><td><code>/api/v1/certs</code></td><td>List your certificates</td></tr>
    <tr><td>POST</td><td><code>/api/v1/certs</code></td><td>Issue a certificate (server generates the key pair)</td></tr>
    <tr><td>POST</td><td><code>/api/v1/certs/sign</code></td><td>Sign an external CSR, returns the certificate</td></tr>
    <tr><td>GET</td><td><code>/api/v1/certs/{id}</code></td><td>Certificate details (your own)</td></tr>
    <tr><td>GET</td><td><code>/api/v1/certs/{id}/pem</code></td><td>Download PEM (your own)</td></tr>
    <tr><td>GET</td><td><code>/api/v1/csr</code>, <code>/csr/{id}</code></td><td>List / check CSR jobs</td></tr>
  </tbody>
</table>

<h2>Examples</h2>
<h3>List your certificates</h3>
<pre><code>curl -H "X-API-Key: pki_YOUR_API_KEY" <%=e(base)%>/api/v1/certs</code></pre>

<h3>Issue a certificate</h3>
<pre><code>curl -X POST \
  -H "X-API-Key: pki_YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "commonName": "app.example.local",
    "certType": "SERVER",
    "sanDns": "app.example.local,www.example.local",
    "keySize": 2048,
    "caId": 1
  }' \
  <%=e(base)%>/api/v1/certs</code></pre>
<p>The <code>201</code> response includes <code>certificatePem</code> and, for generated keys,
   <code>privateKeyPem</code> (returned only at creation). If the client has a default CA, <code>caId</code>
   may be omitted.</p>

<h3>Sign an external CSR</h3>
<pre><code>CSR=$(awk '{printf "%s\\n", $0}' request.csr)
curl -X POST \
  -H "X-API-Key: pki_YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"csrPem\": \"$CSR\", \"certType\": \"SERVER\", \"caId\": 1}" \
  <%=e(base)%>/api/v1/certs/sign</code></pre>

<div class="doc-warn"><i class="bi bi-exclamation-triangle me-1"></i>
  Approving a client lets it issue from <strong>any active CA</strong> via <code>caId</code>; the client's
  preferred CA is only the default. Approve accordingly, and keep the key secret — anyone with it can issue
  certificates as that client.</div>
<p>Manage clients programmatically via <a href="<%=ctx%>/docs/scim">SCIM</a> (<code>/ApiClients</code>).</p>
