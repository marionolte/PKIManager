<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>
<!DOCTYPE html><html lang="de">
<head><meta charset="UTF-8"/><title>PKI Manager – Error</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"/>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css"/>
<style>body{background:#f0f4f8;font-family:'Segoe UI',sans-serif;}</style>
</head>
<body>
<div class="d-flex align-items-center justify-content-center min-vh-100">
  <div class="text-center">
    <i class="bi bi-shield-exclamation" style="font-size:4rem;color:#1b4f8a;opacity:.4;"></i>
    <h2 class="mt-3 fw-bold">
      <% Integer sc=(Integer)request.getAttribute("jakarta.servlet.error.status_code");
         if(sc!=null&&sc==404){%>Page Not Found<%}else{%>Something went wrong<%}%>
    </h2>
    <p class="text-muted">
      <% Throwable t=(Throwable)request.getAttribute("jakarta.servlet.error.exception");
         if(t!=null&&t.getMessage()!=null){%><%=t.getMessage()%><%}%>
    </p>
    <a href="<%=request.getContextPath()%>/dashboard" class="btn btn-primary mt-2">
      <i class="bi bi-house me-1"></i>Back to Dashboard
    </a>
  </div>
</div>
</body></html>
