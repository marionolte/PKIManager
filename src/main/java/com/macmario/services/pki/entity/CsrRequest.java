package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

public class CsrRequest {
    public enum Status { PENDING, SIGNED, REJECTED }

    private Long id;
    private String trackingToken;
    private String csrPem;
    private String subjectCn;
    private String requesterName;
    private String requesterEmail;
    private String requesterNotes;
    private Status status = Status.PENDING;
    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime processedAt;
    private Long signedCertId;
    private Long signedCaId;
    private String adminNotes;
    private String issuingCaName;
    private Long apiClientId;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getTrackingToken() { return trackingToken; }
    public void setTrackingToken(String v) { trackingToken = v; }
    public String getCsrPem() { return csrPem; }
    public void setCsrPem(String v) { csrPem = v; }
    public String getSubjectCn() { return subjectCn; }
    public void setSubjectCn(String v) { subjectCn = v; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String v) { requesterName = v; }
    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String v) { requesterEmail = v; }
    public String getRequesterNotes() { return requesterNotes; }
    public void setRequesterNotes(String v) { requesterNotes = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { status = v; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime v) { requestedAt = v; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime v) { processedAt = v; }
    public Long getSignedCertId() { return signedCertId; }
    public void setSignedCertId(Long v) { signedCertId = v; }
    public Long getSignedCaId() { return signedCaId; }
    public void setSignedCaId(Long v) { signedCaId = v; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String v) { adminNotes = v; }
    public String getIssuingCaName() { return issuingCaName; }
    public void setIssuingCaName(String v) { issuingCaName = v; }
    public Long getApiClientId() { return apiClientId; }
    public void setApiClientId(Long v) { apiClientId = v; }
}
