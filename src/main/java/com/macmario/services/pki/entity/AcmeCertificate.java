package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

public class AcmeCertificate {
    public enum AcmeStatus { PENDING, ACTIVE, EXPIRED, ERROR }

    private Long id;
    private String domain;
    private String accountUrl;
    private String accountKeyPem;
    private String certPem;
    private String chainPem;
    private String privateKeyPem;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private boolean autoRenew = true;
    private LocalDateTime lastRenewedAt;
    private AcmeStatus status = AcmeStatus.PENDING;
    private String errorMessage;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getDomain() { return domain; }
    public void setDomain(String v) { domain = v; }
    public String getAccountUrl() { return accountUrl; }
    public void setAccountUrl(String v) { accountUrl = v; }
    public String getAccountKeyPem() { return accountKeyPem; }
    public void setAccountKeyPem(String v) { accountKeyPem = v; }
    public String getCertPem() { return certPem; }
    public void setCertPem(String v) { certPem = v; }
    public String getChainPem() { return chainPem; }
    public void setChainPem(String v) { chainPem = v; }
    public String getPrivateKeyPem() { return privateKeyPem; }
    public void setPrivateKeyPem(String v) { privateKeyPem = v; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime v) { validFrom = v; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime v) { validUntil = v; }
    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean v) { autoRenew = v; }
    public LocalDateTime getLastRenewedAt() { return lastRenewedAt; }
    public void setLastRenewedAt(LocalDateTime v) { lastRenewedAt = v; }
    public AcmeStatus getStatus() { return status; }
    public void setStatus(AcmeStatus v) { status = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }

    public boolean isExpired() { return validUntil != null && LocalDateTime.now().isAfter(validUntil); }
    public boolean needsRenewal() { return validUntil != null && LocalDateTime.now().plusDays(30).isAfter(validUntil); }
}
