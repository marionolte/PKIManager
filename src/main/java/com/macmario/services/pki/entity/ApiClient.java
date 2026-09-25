package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

public class ApiClient {
    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }

    private Long id;
    private String name;
    private String apiKey;
    private String description;
    private boolean active = true;
    private Long defaultCaId;
    private String defaultCaName;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private String lastIp;
    private String lastUserAgent;
    private Long ownerUserId;
    private String ownerName;                 // denormalized (PKI_USER.username)
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;
    private String approvedBy;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { apiKey = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public Long getDefaultCaId() { return defaultCaId; }
    public void setDefaultCaId(Long v) { defaultCaId = v; }
    public String getDefaultCaName() { return defaultCaName; }
    public void setDefaultCaName(String v) { defaultCaName = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime v) { lastUsedAt = v; }
    public String getLastIp() { return lastIp; }
    public void setLastIp(String v) { lastIp = v; }
    public String getLastUserAgent() { return lastUserAgent; }
    public void setLastUserAgent(String v) { lastUserAgent = v; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long v) { ownerUserId = v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { ownerName = v; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(ApprovalStatus v) { approvalStatus = v; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String v) { approvedBy = v; }

    public boolean isApproved() { return approvalStatus == ApprovalStatus.APPROVED; }
    public boolean isPending()  { return approvalStatus == ApprovalStatus.PENDING; }
    public boolean isRejected() { return approvalStatus == ApprovalStatus.REJECTED; }
}
