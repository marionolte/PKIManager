<%@ page contentType="text/html;charset=UTF-8" %>
<% String ctx=request.getContextPath(); %>
<h1><i class="bi bi-file-earmark-lock2 me-2 text-success"></i>Certificates</h1>
<p>PKI Manager issues X.509 certificates from your Certificate Authorities and tracks their full
   lifecycle (valid → revoked / expired).</p>

<h2>Issuing a certificate</h2>
<p>Open <a href="<%=ctx%>/cert/issue">Issue Certificate</a> and choose an <strong>active</strong> issuing CA.
   There are two ways to obtain a certificate:</p>
<ul>
  <li><strong>Generate key + certificate</strong> — PKI Manager creates the RSA key pair and the
      certificate. The private key can be downloaded by authenticated users.</li>
  <li><strong>Sign an external CSR</strong> — paste a PEM PKCS&nbsp;#10 request; the subject is taken from
      the CSR and only the certificate is returned (the private key never leaves your side).</li>
</ul>

<h3>Certificate types</h3>
<table>
  <thead><tr><th>Type</th><th>Key usage</th><th>Typical use</th></tr></thead>
  <tbody>
    <tr><td><code>SERVER</code></td><td>digitalSignature, keyEncipherment</td><td>TLS server certificates</td></tr>
    <tr><td><code>CLIENT</code></td><td>digitalSignature, keyAgreement</td><td>mTLS / client authentication</td></tr>
    <tr><td><code>CODE_SIGNING</code></td><td>digitalSignature, nonRepudiation</td><td>Signing binaries / artefacts</td></tr>
    <tr><td><code>EMAIL</code></td><td>digitalSignature, keyEncipherment, nonRepudiation</td><td>S/MIME</td></tr>
  </tbody>
</table>
<p>Subject Alternative Names (DNS and IP) and, when configured on the CA, CRL Distribution Point and OCSP
   URLs are embedded automatically. An issuing CA restricted with <strong>Name Constraints</strong> may only
   issue for its permitted domains.</p>

<h2>Downloading</h2>
<ul>
  <li>From the certificate detail page (authenticated) — PEM, and the private key when it was generated here.</li>
  <li>Via the <strong>public token link</strong> shown on the detail page:
      <code><%=ctx%>/public/download/{token}</code> — serves the PEM <em>without login</em> so servers can
      import it automatically. Share the link only with trusted systems.</li>
</ul>

<h2>Revocation</h2>
<p>On a certificate's detail page choose <strong>Revoke</strong> and an RFC&nbsp;5280 reason code. Revocation
   is recorded in the revocation audit trail. Revoking a CA cascades: every valid certificate it issued
   (and every descendant CA) is revoked too.</p>
<div class="doc-warn"><i class="bi bi-exclamation-triangle me-1"></i>
  This deployment does not publish a CRL, so revocation is a database-level trust marker. Distribute trust
  changes to relying parties out of band where needed.</div>

<h2>Self-service CSR portal</h2>
<p>External requesters without an account can submit a CSR at <code><%=ctx%>/public/csr</code> and track it
   with the returned link. An admin signs or rejects it under <strong>CSR Jobs</strong>.</p>

<h2>Status values</h2>
<ul>
  <li><code>VALID</code> — active and within its validity window.</li>
  <li><code>REVOKED</code> — permanently revoked; keep for audit.</li>
  <li><code>EXPIRED</code> — past its <em>notAfter</em> date.</li>
</ul>
<p>See also the <a href="<%=ctx%>/docs/api-clients">API Clients</a> guide to issue certificates
   programmatically, and <a href="<%=ctx%>/docs/scim">SCIM</a> to revoke them via provisioning.</p>
