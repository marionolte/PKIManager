package com.macmario.services.pki.entity;

import java.time.LocalDateTime;

public class CertificateRecord {
    public enum CertStatus { VALID, REVOKED, EXPIRED }
    public enum CertType   { SERVER, CLIENT, CODE_SIGNING, EMAIL, CA }

    private Long id;
    private Long issuingCaId;
    private String issuingCaDisplayName;
    private String serialNumber;
    private CertStatus certStatus = CertStatus.VALID;
    private CertType certType = CertType.SERVER;
    private String country, state, locality, organization, orgUnit, commonName, emailAddress;
    private String sanDns, sanIp;
    private LocalDateTime validFrom, validUntil;
    private int keySize = 2048;
    private String signatureAlgorithm = "SHA256withRSA";
    private String certificatePem, csrPem, privateKeyPem;
    private String fingerprintSha256;
    private LocalDateTime issuedAt = LocalDateTime.now();
    private String requester, notes;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getIssuingCaId() { return issuingCaId; }
    public void setIssuingCaId(Long v) { issuingCaId = v; }
    public String getIssuingCaDisplayName() { return issuingCaDisplayName; }
    public void setIssuingCaDisplayName(String v) { issuingCaDisplayName = v; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { serialNumber = v; }
    public CertStatus getCertStatus() { return certStatus; }
    public void setCertStatus(CertStatus v) { certStatus = v; }
    public CertType getCertType() { return certType; }
    public void setCertType(CertType v) { certType = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { country = v; }
    public String getState() { return state; }
    public void setState(String v) { state = v; }
    public String getLocality() { return locality; }
    public void setLocality(String v) { locality = v; }
    public String getOrganization() { return organization; }
    public void setOrganization(String v) { organization = v; }
    public String getOrgUnit() { return orgUnit; }
    public void setOrgUnit(String v) { orgUnit = v; }
    public String getCommonName() { return commonName; }
    public void setCommonName(String v) { commonName = v; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String v) { emailAddress = v; }
    public String getSanDns() { return sanDns; }
    public void setSanDns(String v) { sanDns = v; }
    public String getSanIp() { return sanIp; }
    public void setSanIp(String v) { sanIp = v; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime v) { validFrom = v; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime v) { validUntil = v; }
    public int getKeySize() { return keySize; }
    public void setKeySize(int v) { keySize = v; }
    public String getSignatureAlgorithm() { return signatureAlgorithm; }
    public void setSignatureAlgorithm(String v) { signatureAlgorithm = v; }
    public String getCertificatePem() { return certificatePem; }
    public void setCertificatePem(String v) { certificatePem = v; }
    public String getCsrPem() { return csrPem; }
    public void setCsrPem(String v) { csrPem = v; }
    public String getPrivateKeyPem() { return privateKeyPem; }
    public void setPrivateKeyPem(String v) { privateKeyPem = v; }
    public String getFingerprintSha256() { return fingerprintSha256; }
    public void setFingerprintSha256(String v) { fingerprintSha256 = v; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime v) { issuedAt = v; }
    public String getRequester() { return requester; }
    public void setRequester(String v) { requester = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }

    public boolean isExpired()      { return validUntil != null && LocalDateTime.now().isAfter(validUntil); }
    public boolean isExpiringSoon() { return validUntil != null && !isExpired() && LocalDateTime.now().plusDays(30).isAfter(validUntil); }
}
