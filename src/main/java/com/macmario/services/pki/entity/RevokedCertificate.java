package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

public class RevokedCertificate {
    public enum RevocationReason {
        UNSPECIFIED, KEY_COMPROMISE, CA_COMPROMISE, AFFILIATION_CHANGED,
        SUPERSEDED, CESSATION_OF_OPERATION, CERTIFICATE_HOLD,
        PRIVILEGE_WITHDRAWN, AA_COMPROMISE
    }
    private Long id;
    private Long certificateId;
    private String serialNumber;
    private LocalDateTime revokedAt = LocalDateTime.now();
    private RevocationReason reason = RevocationReason.UNSPECIFIED;
    private String revokedBy, comment;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getCertificateId() { return certificateId; }
    public void setCertificateId(Long v) { certificateId = v; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { serialNumber = v; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime v) { revokedAt = v; }
    public RevocationReason getReason() { return reason; }
    public void setReason(RevocationReason v) { reason = v; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String v) { revokedBy = v; }
    public String getComment() { return comment; }
    public void setComment(String v) { comment = v; }
}
