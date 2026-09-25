<%@ page contentType="text/html;charset=UTF-8" %>
<%! private String e(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");} %>
<% String ctx=request.getContextPath(); String base=(String)request.getAttribute("appBase"); String scim=base+"/scim/v2"; %>
<h1><i class="bi bi-people me-2 text-info"></i>SCIM 2.0 Provisioning</h1>
<p>A <a href="https://datatracker.ietf.org/doc/html/rfc7644" target="_blank" rel="noopener">SCIM&nbsp;2.0</a>
   endpoint lets an identity provider (Entra&nbsp;ID, Okta, …) or scripts manage identities and resources.
   Base URL: <code><%=e(scim)%></code></p>

<h2>Authentication</h2>
<p>Send a static bearer token: <code>Authorization: Bearer &lt;token&gt;</code>. An administrator generates
   or rotates it on the <a href="<%=ctx%>/admin/users/">Users</a> admin page (only its SHA-256 is stored,
   shown once). If no token is set, SCIM returns <code>401</code> (disabled). Sessions and API-client keys are
   <strong>not</strong> accepted here.</p>

<h2>Resources</h2>
<table>
  <thead><tr><th>Resource</th><th>Endpoint</th><th>Maps to</th><th>Operations</th></tr></thead>
  <tbody>
    <tr><td>Users</td><td><code>/Users</code></td><td>PKI users</td><td>list, get, PUT, PATCH</td></tr>
    <tr><td>Groups</td><td><code>/Groups</code></td><td>roles ADMIN / VIEWER</td><td>list, get, PUT, PATCH</td></tr>
    <tr><td>Certificates</td><td><code>/Certificates</code></td><td>issued certificates</td><td>list, get, PATCH (revoke)</td></tr>
    <tr><td>ApiClients</td><td><code>/ApiClients</code></td><td>API clients</td><td>list, get, PUT, PATCH</td></tr>
  </tbody>
</table>
<p>Discovery: <code>/ServiceProviderConfig</code>, <code>/ResourceTypes</code>, <code>/Schemas</code>.</p>

<h2>Roles as Groups</h2>
<p>A user's <code>role</code> is single-valued, so <strong>ADMIN</strong> membership is authoritative and
   <strong>VIEWER</strong> is its complement:</p>
<ul>
  <li>Add a user to <code>ADMIN</code> → promoted to ADMIN.</li>
  <li>Remove from <code>ADMIN</code> → demoted to VIEWER.</li>
  <li>Any change that would leave <strong>no active administrator</strong> is refused (<code>400</code>).</li>
</ul>
<div class="doc-note"><i class="bi bi-info-circle me-1"></i>
  Deprovisioning is immediate: a SCIM <code>active=false</code> or role change ends the affected web session on
  the user's next request. Responses never contain secrets (password hashes, private keys, API keys).</div>

<h2>Examples</h2>
<h3>List users, filter by userName</h3>
<pre><code>curl -H "Authorization: Bearer $TOKEN" \
  "<%=e(scim)%>/Users?filter=userName%20eq%20%22alice%22"</code></pre>

<h3>Deactivate a user (PATCH)</h3>
<pre><code>curl -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/scim+json" \
  -d '{"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
       "Operations":[{"op":"replace","path":"active","value":false}]}' \
  <%=e(scim)%>/Users/42</code></pre>

<h3>Promote a user to ADMIN (Group PATCH)</h3>
<pre><code>curl -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/scim+json" \
  -d '{"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
       "Operations":[{"op":"add","path":"members","value":[{"value":"42"}]}]}' \
  <%=e(scim)%>/Groups/ADMIN</code></pre>

<h3>Revoke a certificate</h3>
<pre><code>curl -X PATCH -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/scim+json" \
  -d '{"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
       "Operations":[{"op":"replace","path":"status","value":"REVOKED"}]}' \
  <%=e(scim)%>/Certificates/7</code></pre>

<h2>Notes &amp; limits</h2>
<ul>
  <li>Certificates are read-only except transitioning <code>status</code> to <code>REVOKED</code>.</li>
  <li>ApiClients honour the approval workflow — activating a non-approved client returns <code>400</code>;
      set <code>approvalStatus</code> to <code>APPROVED</code>/<code>REJECTED</code> to approve/reject.</li>
  <li>Filtering supports <code>attr eq "value"</code>; pagination uses <code>startIndex</code> / <code>count</code>.</li>
  <li><code>POST</code> (create) and <code>DELETE</code> are not implemented in this version.</li>
</ul>
