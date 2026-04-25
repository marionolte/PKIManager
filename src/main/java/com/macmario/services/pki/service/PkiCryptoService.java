package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.entity.CertificateRecord;
import com.macmario.services.pki.entity.RevokedCertificate;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Core cryptographic PKI operations using Bouncy Castle.
 * Handles CA initialisation, CSR signing, and certificate management.
 */
public class PkiCryptoService {

    private static final Logger log = LoggerFactory.getLogger(PkiCryptoService.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ──────────────────────────────────────────
    // CA Initialisation
    // ──────────────────────────────────────────

    /**
     * Generate a self-signed Root CA certificate and populate CaConfig with PEM data.
     */
    public void initRootCa(CaConfig ca) throws Exception {
        log.info("Generating Root CA: {}", ca.getCommonName());

        KeyPair keyPair = generateKeyPair(ca.getKeySize());
        X500Name subject = buildX500Name(ca);
        BigInteger serial = generateSerial();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(ca.getDefaultDays());

        ContentSigner signer = new JcaContentSignerBuilder(sigAlg(ca.getDefaultMd()))
                .setProvider("BC").build(keyPair.getPrivate());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, serial,
                toDate(now), toDate(expiry),
                subject, keyPair.getPublic());

        // CA extensions
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                createSubjectKeyId(keyPair.getPublic()));

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(builder.build(signer));

        ca.setCertificatePem(toPem(cert));
        ca.setPrivateKeyPem(toPem(keyPair.getPrivate()));
        ca.setSerialNumber(serial.toString(16).toUpperCase());
        ca.setValidFrom(now);
        ca.setValidUntil(expiry);
        log.info("Root CA generated, serial={}", ca.getSerialNumber());
    }

    /**
     * Generate an Intermediate / Issuing CA certificate signed by the parent CA.
     */
    public void initSubCa(CaConfig ca, CaConfig parentCa) throws Exception {
        log.info("Generating Sub CA: {} signed by {}", ca.getCommonName(), parentCa.getCommonName());

        KeyPair keyPair = generateKeyPair(ca.getKeySize());
        X500Name subject = buildX500Name(ca);
        X500Name issuer = buildX500Name(parentCa);
        BigInteger serial = generateSerial();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(ca.getDefaultDays());

        PrivateKey parentKey = readPrivateKey(parentCa.getPrivateKeyPem());
        ContentSigner signer = new JcaContentSignerBuilder(sigAlg(parentCa.getDefaultMd()))
                .setProvider("BC").build(parentKey);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial,
                toDate(now), toDate(expiry),
                subject, keyPair.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                createSubjectKeyId(keyPair.getPublic()));

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(builder.build(signer));

        ca.setCertificatePem(toPem(cert));
        ca.setPrivateKeyPem(toPem(keyPair.getPrivate()));
        ca.setSerialNumber(serial.toString(16).toUpperCase());
        ca.setValidFrom(now);
        ca.setValidUntil(expiry);
        log.info("Sub CA generated, serial={}", ca.getSerialNumber());
    }

    // ──────────────────────────────────────────
    // Certificate Issuance
    // ──────────────────────────────────────────

    /**
     * Issue a certificate from a CSR, signed by the given CA.
     * Returns a populated CertificateRecord (not yet persisted).
     */
    public CertificateRecord signCsr(String csrPem, CaConfig ca, CertificateRecord template) throws Exception {
        PKCS10CertificationRequest csr = (PKCS10CertificationRequest)
                new PEMParser(new StringReader(csrPem)).readObject();
        JcaPKCS10CertificationRequest jcaCsr = new JcaPKCS10CertificationRequest(csr).setProvider("BC");

        X500Name issuer = buildX500Name(ca);
        BigInteger serial = generateSerial();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(ca.getDefaultDays());

        PrivateKey caKey = readPrivateKey(ca.getPrivateKeyPem());
        ContentSigner signer = new JcaContentSignerBuilder(sigAlg(ca.getDefaultMd()))
                .setProvider("BC").build(caKey);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial,
                toDate(now), toDate(expiry),
                csr.getSubject(), jcaCsr.getPublicKey());

        // Basic extensions
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        applyKeyUsage(builder, template.getCertType());
        addSanExtension(builder, template);
        if (ca.getCrlUrl() != null && !ca.getCrlUrl().isEmpty()) {
            addCrlDistPoint(builder, ca.getCrlUrl());
        }

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(builder.build(signer));

        template.setCertificatePem(toPem(cert));
        template.setCsrPem(csrPem);
        template.setSerialNumber(serial.toString(16).toUpperCase());
        template.setValidFrom(now);
        template.setValidUntil(expiry);
        template.setFingerprintSha256(fingerprint(cert));
        template.setIssuingCaId(ca.getId());
        template.setIssuingCaDisplayName(ca.getDisplayName());
        return template;
    }

    /**
     * Generate a key pair + CSR for a new certificate and sign it immediately.
     * Useful for server/client certs managed entirely by PKI Manager.
     */
    public CertificateRecord generateAndSign(CaConfig ca, CertificateRecord template) throws Exception {
        KeyPair keyPair = generateKeyPair(template.getKeySize());
        X500Name subject = buildX500NameFromCert(template);

        JcaPKCS10CertificationRequestBuilder csrBuilder =
                new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
        ContentSigner csrSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(keyPair.getPrivate());
        PKCS10CertificationRequest csr = csrBuilder.build(csrSigner);

        String csrPem = toPem(csr);
        signCsr(csrPem, ca, template);
        template.setPrivateKeyPem(toPem(keyPair.getPrivate()));
        return template;
    }

    // ──────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────

    private KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", "BC");
        gen.initialize(keySize, new SecureRandom());
        return gen.generateKeyPair();
    }

    private BigInteger generateSerial() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return new BigInteger(1, bytes);
    }

    private X500Name buildX500Name(CaConfig ca) {
        X500NameBuilder b = new X500NameBuilder(BCStyle.INSTANCE);
        if (ca.getCountry() != null)      b.addRDN(BCStyle.C, ca.getCountry());
        if (ca.getState() != null)        b.addRDN(BCStyle.ST, ca.getState());
        if (ca.getLocality() != null)     b.addRDN(BCStyle.L, ca.getLocality());
        if (ca.getOrganization() != null) b.addRDN(BCStyle.O, ca.getOrganization());
        if (ca.getOrgUnit() != null)      b.addRDN(BCStyle.OU, ca.getOrgUnit());
        b.addRDN(BCStyle.CN, ca.getCommonName());
        if (ca.getEmailAddress() != null) b.addRDN(BCStyle.EmailAddress, ca.getEmailAddress());
        return b.build();
    }

    private X500Name buildX500NameFromCert(CertificateRecord cr) {
        X500NameBuilder b = new X500NameBuilder(BCStyle.INSTANCE);
        if (cr.getCountry() != null)      b.addRDN(BCStyle.C, cr.getCountry());
        if (cr.getState() != null)        b.addRDN(BCStyle.ST, cr.getState());
        if (cr.getLocality() != null)     b.addRDN(BCStyle.L, cr.getLocality());
        if (cr.getOrganization() != null) b.addRDN(BCStyle.O, cr.getOrganization());
        if (cr.getOrgUnit() != null)      b.addRDN(BCStyle.OU, cr.getOrgUnit());
        b.addRDN(BCStyle.CN, cr.getCommonName());
        if (cr.getEmailAddress() != null) b.addRDN(BCStyle.EmailAddress, cr.getEmailAddress());
        return b.build();
    }

    private PrivateKey readPrivateKey(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                return new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter()
                        .setProvider("BC").getKeyPair(kp).getPrivate();
            }
            if (obj instanceof org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo) {
                throw new IllegalArgumentException("Encrypted private keys not supported without password");
            }
            if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                return new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter()
                        .setProvider("BC").getPrivateKey(pki);
            }
            throw new IllegalArgumentException("Cannot parse private key PEM: " + obj.getClass());
        }
    }

    private String toPem(Object obj) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(obj);
        }
        return sw.toString();
    }

    private Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String sigAlg(String md) {
        if (md == null) return "SHA256withRSA";
        return switch (md.toLowerCase()) {
            case "sha384" -> "SHA384withRSA";
            case "sha512" -> "SHA512withRSA";
            default       -> "SHA256withRSA";
        };
    }

    private SubjectKeyIdentifier createSubjectKeyId(PublicKey pub) throws Exception {
        return new SubjectKeyIdentifier(pub.getEncoded());
    }

    private void applyKeyUsage(X509v3CertificateBuilder b, CertificateRecord.CertType type) throws Exception {
        int usage = switch (type) {
            case SERVER        -> KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
            case CLIENT        -> KeyUsage.digitalSignature | KeyUsage.keyAgreement;
            case CODE_SIGNING  -> KeyUsage.digitalSignature | KeyUsage.nonRepudiation;
            case EMAIL         -> KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.nonRepudiation;
            default            -> KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
        };
        b.addExtension(Extension.keyUsage, true, new KeyUsage(usage));
    }

    private void addSanExtension(X509v3CertificateBuilder b, CertificateRecord cr) throws Exception {
        List<GeneralName> names = new ArrayList<>();
        if (cr.getSanDns() != null && !cr.getSanDns().isBlank()) {
            for (String dns : cr.getSanDns().split(",")) {
                String d = dns.trim();
                if (!d.isEmpty()) names.add(new GeneralName(GeneralName.dNSName, d));
            }
        }
        if (cr.getSanIp() != null && !cr.getSanIp().isBlank()) {
            for (String ip : cr.getSanIp().split(",")) {
                String i = ip.trim();
                if (!i.isEmpty()) names.add(new GeneralName(GeneralName.iPAddress, i));
            }
        }
        if (!names.isEmpty()) {
            b.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(names.toArray(new GeneralName[0])));
        }
    }

    private void addCrlDistPoint(X509v3CertificateBuilder b, String crlUrl) throws Exception {
        GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, crlUrl);
        DistributionPointName dpn = new DistributionPointName(new GeneralNames(gn));
        DistributionPoint dp = new DistributionPoint(dpn, null, null);
        b.addExtension(Extension.cRLDistributionPoints, false,
                new CRLDistPoint(new DistributionPoint[]{dp}));
    }

    private String fingerprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] der = cert.getEncoded();
        byte[] hash = md.digest(der);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02X:", b & 0xFF));
        }
        if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}
