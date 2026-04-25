package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

/** Plain Java object representing a Certificate Authority row. */
public class CaConfig {
    public enum CaType   { ROOT, INTERMEDIATE, ISSUING }
    public enum CaStatus { ACTIVE, DISABLED, EXPIRED }

    private Long id;
    private String roleName;
    private String displayName;
    private CaType caType = CaType.ROOT;
    private CaStatus status = CaStatus.ACTIVE;
    private Long parentCaId;
    private String parentCaDisplayName; // denormalized for display
    private String country;
    private String state;
    private String locality;
    private String organization;
    private String orgUnit;
    private String commonName;
    private String emailAddress;
    private int defaultDays = 730;
    private String defaultMd = "sha256";
    private int keySize = 4096;
    private String crlUrl;
    private String ocspUrl;
    private String certificatePem;
    private String privateKeyPem;
    private String serialNumber;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String v) { this.roleName = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public CaType getCaType() { return caType; }
    public void setCaType(CaType v) { this.caType = v; }
    public CaStatus getStatus() { return status; }
    public void setStatus(CaStatus v) { this.status = v; }
    public Long getParentCaId() { return parentCaId; }
    public void setParentCaId(Long v) { this.parentCaId = v; }
    public String getParentCaDisplayName() { return parentCaDisplayName; }
    public void setParentCaDisplayName(String v) { this.parentCaDisplayName = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getLocality() { return locality; }
    public void setLocality(String v) { this.locality = v; }
    public String getOrganization() { return organization; }
    public void setOrganization(String v) { this.organization = v; }
    public String getOrgUnit() { return orgUnit; }
    public void setOrgUnit(String v) { this.orgUnit = v; }
    public String getCommonName() { return commonName; }
    public void setCommonName(String v) { this.commonName = v; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String v) { this.emailAddress = v; }
    public int getDefaultDays() { return defaultDays; }
    public void setDefaultDays(int v) { this.defaultDays = v; }
    public String getDefaultMd() { return defaultMd; }
    public void setDefaultMd(String v) { this.defaultMd = v; }
    public int getKeySize() { return keySize; }
    public void setKeySize(int v) { this.keySize = v; }
    public String getCrlUrl() { return crlUrl; }
    public void setCrlUrl(String v) { this.crlUrl = v; }
    public String getOcspUrl() { return ocspUrl; }
    public void setOcspUrl(String v) { this.ocspUrl = v; }
    public String getCertificatePem() { return certificatePem; }
    public void setCertificatePem(String v) { this.certificatePem = v; }
    public String getPrivateKeyPem() { return privateKeyPem; }
    public void setPrivateKeyPem(String v) { this.privateKeyPem = v; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { this.serialNumber = v; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime v) { this.validFrom = v; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime v) { this.validUntil = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }

    public boolean isExpired() {
        return validUntil != null && LocalDateTime.now().isAfter(validUntil);
    }
    public boolean isExpiringSoon() {
        return validUntil != null && !isExpired() && LocalDateTime.now().plusDays(30).isAfter(validUntil);
    }
}
