<%@ page contentType="text/html;charset=UTF-8" %>
<% String ctx=request.getContextPath(); %>
<h1><i class="bi bi-lock me-2 text-warning"></i>ACME / Let's Encrypt</h1>
<p>PKI Manager includes an ACME v2 (<a href="https://datatracker.ietf.org/doc/html/rfc8555" target="_blank" rel="noopener">RFC&nbsp;8555</a>)
   client that obtains <strong>publicly-trusted</strong> TLS certificates from Let's Encrypt for a public
   domain — separate from your internal CAs.</p>

<h2>How it works</h2>
<ol>
  <li>Open <a href="<%=ctx%>/admin/acme">ACME / Let's Encrypt</a> and register a domain (choose production or
      staging, and a contact e-mail).</li>
  <li>Trigger a certificate request. The full flow runs asynchronously (~60&nbsp;s):
      directory → nonce → account → order → HTTP-01 challenge → finalize → download.</li>
  <li>PKI Manager serves the challenge token at
      <code>/.well-known/acme-challenge/{token}</code> for the validation server to fetch.</li>
  <li>The issued certificate, chain and private key are stored and shown in the ACME list.</li>
</ol>

<h2>Requirements</h2>
<ul>
  <li>The server must be reachable from the internet on <strong>port&nbsp;80</strong> for the HTTP-01 challenge
      (the <code>/.well-known/acme-challenge/*</code> path is public).</li>
  <li>DNS for the domain must resolve to this server.</li>
  <li>Account keys use EC P-256 with ES256 JWS signing throughout the flow.</li>
</ul>

<div class="doc-note"><i class="bi bi-info-circle me-1"></i>
  Use the <strong>staging</strong> environment first to validate your setup — Let's Encrypt production has
  strict rate limits. Switch to production once staging succeeds.</div>

<h2>Production vs. staging</h2>
<table>
  <thead><tr><th>Environment</th><th>Trusted?</th><th>Use for</th></tr></thead>
  <tbody>
    <tr><td>Staging</td><td>No (test roots)</td><td>Validating challenge reachability and the flow</td></tr>
    <tr><td>Production</td><td>Yes</td><td>Real, browser-trusted certificates</td></tr>
  </tbody>
</table>

<div class="doc-warn"><i class="bi bi-exclamation-triangle me-1"></i>
  ACME issues certificates for <strong>public</strong> domains you control. For internal hostnames
  (e.g. <code>.int</code>, <code>.local</code>) use your internal CAs instead — see the
  <a href="<%=ctx%>/docs/certificates">Certificates</a> guide.</div>

<h2>Renewal</h2>
<p>Managed ACME certificates track their validity and renewal status in the ACME list. Re-trigger a request
   before expiry to renew; auto-renew is flagged per entry.</p>
