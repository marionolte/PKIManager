package com.macmario.services.pki.service;

import com.macmario.services.pki.entity.CaConfig;
import com.macmario.services.pki.entity.CertificateRecord;
import com.macmario.services.pki.entity.RevokedCertificate;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
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
        // Always replace: a stale provider from a previous webapp classloader would
        // cause ClassNotFoundException on hot-redeploy.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new BouncyCastleProvider());
    }

    // ──────────────────────────────────────────
    // CA Initialisation
    // ──────────────────────────────────────────

    /**
     * Generate a self-signed Root CA certificate and populate CaConfig with PEM data.
     */
    public void initRootCa(CaConfig ca) throws GeneralSecurityException, OperatorCreationException, IOException {
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
    public void initSubCa(CaConfig ca, CaConfig parentCa) throws GeneralSecurityException, OperatorCreationException, IOException {
        log.info("Generating Sub CA: {} signed by {}", ca.getCommonName(), parentCa.getCommonName());

        // A parent whose certificate carries pathLenConstraint=0 may sign end-entity certs
        // but not further CAs; signing a sub CA under it would produce an invalid chain.
        if (parentCa.getCertificatePem() != null && !parentCa.getCertificatePem().isBlank()
                && readCertificate(parentCa.getCertificatePem()).getBasicConstraints() == 0) {
            throw new IllegalArgumentException(
                "Parent CA '" + parentCa.getDisplayName() + "' has pathLenConstraint=0 and cannot sign sub CAs");
        }

        KeyPair keyPair = generateKeyPair(ca.getKeySize());
        X500Name subject = buildX500Name(ca);
        X500Name issuer = issuerNameOf(parentCa);
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

        // X.509 Name Constraints: cryptographically restrict what this internal CA may issue for.
        addNameConstraints(builder, ca.getPermittedDomains());

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(builder.build(signer));

        ca.setCertificatePem(toPem(cert));
        ca.setPrivateKeyPem(toPem(keyPair.getPrivate()));
        ca.setSerialNumber(serial.toString(16).toUpperCase());
        ca.setValidFrom(now);
        ca.setValidUntil(expiry);
        log.info("Sub CA generated, serial={}", ca.getSerialNumber());
    }

    /**
     * Add an X.509 Name Constraints extension (permittedSubtrees) for the given
     * comma/space/newline-separated domains. For each domain a dNSName and an
     * rfc822Name subtree is permitted, so e.g. "int" restricts the CA to hosts
     * and e-mail addresses under the .int domain. No-op when {@code permitted}
     * is null/blank.
     */
    private void addNameConstraints(X509v3CertificateBuilder builder, String permitted) throws IOException {
        if (permitted == null || permitted.isBlank()) return;
        List<GeneralSubtree> subtrees = new ArrayList<>();
        for (String raw : permitted.split("[,\\s]+")) {
            String domain = raw.trim();
            if (domain.isEmpty()) continue;
            // RFC 5280 §4.2.1.10: a dNSName constraint matches by right-anchored label
            // suffix and takes NO leading dot ("int" permits host.int). An rfc822Name
            // constraint that should match a whole mail domain DOES take a leading dot
            // (".int" permits any mailbox in the .int domain).
            String dns = domain.startsWith(".") ? domain.substring(1) : domain;
            if (dns.isEmpty()) continue;
            String email = "." + dns;
            subtrees.add(new GeneralSubtree(new GeneralName(GeneralName.dNSName, dns)));
            subtrees.add(new GeneralSubtree(new GeneralName(GeneralName.rfc822Name, email)));
        }
        if (subtrees.isEmpty()) return;
        NameConstraints nc = new NameConstraints(subtrees.toArray(new GeneralSubtree[0]), null);
        builder.addExtension(Extension.nameConstraints, true, nc);
        log.info("Applied Name Constraints permittedSubtrees: {}", permitted);
    }

    /**
     * Import an existing Root or Sub CA from a PEM certificate (or chain) and its
     * RSA private key. Validates the key matches the certificate, that the cert is a
     * usable CA cert, and that the declared type is consistent with the certificate.
     * Populates {@code ca} with the certificate's subject, serial, validity, key size
     * and any existing Name Constraints. The key is re-emitted unencrypted for storage,
     * consistent with the rest of the application.
     *
     * @param keyPassword   password for an encrypted private key, or null/blank if unencrypted
     * @param parentCertPem parent CA certificate PEM for a sub CA (null for a root or an orphan sub CA)
     */
    public void importCa(CaConfig ca, String certPem, String keyPem, String keyPassword, String parentCertPem)
            throws GeneralSecurityException, IOException {
        if (certPem == null || certPem.isBlank()) throw new IllegalArgumentException("Certificate PEM is required");
        if (keyPem == null || keyPem.isBlank())   throw new IllegalArgumentException("Private key PEM is required");

        PrivateKey key = readPrivateKeyFlexible(keyPem, keyPassword);
        if (!(key instanceof RSAPrivateKey rsaKey))
            throw new IllegalArgumentException("Only RSA private keys are supported");

        List<X509Certificate> certs = readCertificates(certPem);
        if (certs.isEmpty())
            throw new IllegalArgumentException("No certificate found in the certificate PEM input");

        // Pick the certificate whose public key matches the private key (doubles as key↔cert check).
        X509Certificate cert = null;
        for (X509Certificate c : certs) {
            if (c.getPublicKey() instanceof RSAPublicKey pub && pub.getModulus().equals(rsaKey.getModulus())) {
                cert = c; break;
            }
        }
        if (cert == null)
            throw new IllegalArgumentException("The private key does not match any certificate in the provided PEM");

        // Must be a CA certificate able to sign other certificates.
        if (cert.getBasicConstraints() == -1)
            throw new IllegalArgumentException("Not a CA certificate: basicConstraints CA:TRUE is required");
        boolean[] ku = cert.getKeyUsage();
        if (ku != null && ku.length > 5 && !ku[5]) // bit 5 == keyCertSign
            throw new IllegalArgumentException("Certificate keyUsage does not permit certificate signing (keyCertSign)");

        boolean selfSigned = cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());
        CaConfig.CaType type = ca.getCaType();
        if (type == CaConfig.CaType.ROOT) {
            if (!selfSigned)
                throw new IllegalArgumentException("A ROOT CA certificate must be self-signed (subject equals issuer)");
            try { cert.verify(cert.getPublicKey()); }
            catch (GeneralSecurityException e) { throw new IllegalArgumentException("Root certificate signature is not valid: " + e.getMessage()); }
        } else {
            if (selfSigned)
                throw new IllegalArgumentException("A self-signed certificate cannot be imported as a " + type + " CA — choose ROOT instead");
        }

        if (parentCertPem != null && !parentCertPem.isBlank()) {
            X509Certificate parentCert = readCertificate(parentCertPem);
            X500Name importedIssuer = new JcaX509CertificateHolder(cert).getIssuer();
            X500Name parentSubject  = new JcaX509CertificateHolder(parentCert).getSubject();
            if (!importedIssuer.equals(parentSubject))
                throw new IllegalArgumentException("The certificate's issuer does not match the selected parent CA's subject");
            try { cert.verify(parentCert.getPublicKey()); }
            catch (GeneralSecurityException e) { throw new IllegalArgumentException("Certificate was not signed by the selected parent CA: " + e.getMessage()); }
        }

        populateDisplayFields(ca, cert);
        ca.setSerialNumber(cert.getSerialNumber().toString(16).toUpperCase());
        ca.setValidFrom(LocalDateTime.ofInstant(cert.getNotBefore().toInstant(), ZoneId.systemDefault()));
        ca.setValidUntil(LocalDateTime.ofInstant(cert.getNotAfter().toInstant(), ZoneId.systemDefault()));
        ca.setKeySize(rsaKey.getModulus().bitLength());
        ca.setPermittedDomains(extractPermittedDomains(cert));
        // Read issuance settings straight from the certificate rather than the form.
        ca.setDefaultMd(digestFromSigAlg(cert));
        ca.setDefaultDays(validityDays(cert));
        ca.setCrlUrl(extractCrlUrl(cert));
        ca.setOcspUrl(extractOcspUrl(cert));
        ca.setCertificatePem(toPem(cert));
        ca.setPrivateKeyPem(toPem(key));
        log.info("Imported {} CA '{}' serial={} md={} days={} crl={} ocsp={}", type, ca.getCommonName(),
                ca.getSerialNumber(), ca.getDefaultMd(), ca.getDefaultDays(), ca.getCrlUrl(), ca.getOcspUrl());
    }

    // ──────────────────────────────────────────
    // Certificate Issuance
    // ──────────────────────────────────────────

    /**
     * Issue a certificate from a CSR, signed by the given CA.
     * Returns a populated CertificateRecord (not yet persisted).
     */
    public CertificateRecord signCsr(String csrPem, CaConfig ca, CertificateRecord template) throws GeneralSecurityException, OperatorCreationException, IOException {
        PKCS10CertificationRequest csr = (PKCS10CertificationRequest)
                new PEMParser(new StringReader(csrPem)).readObject();
        JcaPKCS10CertificationRequest jcaCsr = new JcaPKCS10CertificationRequest(csr).setProvider("BC");

        X500Name issuer = issuerNameOf(ca);
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
    public CertificateRecord generateAndSign(CaConfig ca, CertificateRecord template) throws GeneralSecurityException, OperatorCreationException, IOException {
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

    private KeyPair generateKeyPair(int keySize) throws GeneralSecurityException {
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

    private PrivateKey readPrivateKey(String pem) throws IOException {
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

    /** Parse a private key PEM, decrypting with {@code password} when the key is encrypted. */
    private PrivateKey readPrivateKeyFlexible(String pem, String password) throws IOException {
        JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider("BC");
        char[] pw = (password == null) ? new char[0] : password.toCharArray();
        boolean hasPw = password != null && !password.isEmpty();
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (obj == null)
                throw new IllegalArgumentException("Could not parse private key PEM (empty or invalid)");

            if (obj instanceof PEMEncryptedKeyPair enc) {
                if (!hasPw) throw new IllegalArgumentException("Private key is encrypted; a key password is required");
                try {
                    PEMKeyPair kp = enc.decryptKeyPair(new JcePEMDecryptorProviderBuilder().setProvider("BC").build(pw));
                    return conv.getKeyPair(kp).getPrivate();
                } catch (IOException e) {
                    throw new IllegalArgumentException("Could not decrypt private key (wrong password?)");
                }
            }
            if (obj instanceof PKCS8EncryptedPrivateKeyInfo enc) {
                if (!hasPw) throw new IllegalArgumentException("Private key is encrypted; a key password is required");
                try {
                    InputDecryptorProvider dp = new JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider("BC").build(pw);
                    return conv.getPrivateKey(enc.decryptPrivateKeyInfo(dp));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Could not decrypt private key (wrong password?)");
                }
            }
            if (obj instanceof PEMKeyPair kp) {
                return conv.getKeyPair(kp).getPrivate();
            }
            if (obj instanceof PrivateKeyInfo pki) {
                return conv.getPrivateKey(pki);
            }
            throw new IllegalArgumentException("Unsupported private key format: " + obj.getClass().getSimpleName());
        }
    }

    /** Read the first certificate from a PEM string. */
    private X509Certificate readCertificate(String pem) throws IOException, GeneralSecurityException {
        List<X509Certificate> list = readCertificates(pem);
        if (list.isEmpty()) throw new IllegalArgumentException("No certificate found in PEM input");
        return list.get(0);
    }

    /** Read every certificate in a PEM string (supports a full chain / bundle). */
    private List<X509Certificate> readCertificates(String pem) throws IOException, GeneralSecurityException {
        List<X509Certificate> out = new ArrayList<>();
        JcaX509CertificateConverter conv = new JcaX509CertificateConverter().setProvider("BC");
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj;
            while ((obj = parser.readObject()) != null) {
                if (obj instanceof X509CertificateHolder holder) out.add(conv.getCertificate(holder));
            }
        }
        return out;
    }

    /** Issuer DN for signing: the CA's own certificate subject (exact bytes), falling back to a rebuilt DN. */
    private X500Name issuerNameOf(CaConfig ca) throws IOException, GeneralSecurityException {
        if (ca.getCertificatePem() != null && !ca.getCertificatePem().isBlank()) {
            return new JcaX509CertificateHolder(readCertificate(ca.getCertificatePem())).getSubject();
        }
        return buildX500Name(ca);
    }

    /** Populate the display DN fields of a CaConfig from a certificate subject (lossy; truncated to column widths). */
    private void populateDisplayFields(CaConfig ca, X509Certificate cert) throws IOException, GeneralSecurityException {
        X500Name subj = new JcaX509CertificateHolder(cert).getSubject();
        String cn = firstRdn(subj, BCStyle.CN);
        ca.setCommonName(trunc(cn != null ? cn : cert.getSubjectX500Principal().getName(), 200));
        ca.setOrganization(trunc(firstRdn(subj, BCStyle.O), 200));
        ca.setOrgUnit(trunc(firstRdn(subj, BCStyle.OU), 200));
        ca.setCountry(trunc(firstRdn(subj, BCStyle.C), 3));
        ca.setState(trunc(firstRdn(subj, BCStyle.ST), 100));
        ca.setLocality(trunc(firstRdn(subj, BCStyle.L), 100));
        ca.setEmailAddress(trunc(firstRdn(subj, BCStyle.EmailAddress), 200));
    }

    private String firstRdn(X500Name name, ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        if (rdns.length == 0) return null;
        return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }

    private String trunc(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Extract permitted dNSName subtrees from an existing NameConstraints extension, if any. */
    private String extractPermittedDomains(X509Certificate cert) {
        try {
            byte[] ext = cert.getExtensionValue(Extension.nameConstraints.getId());
            if (ext == null) return null;
            NameConstraints nc = NameConstraints.getInstance(ASN1OctetString.getInstance(ext).getOctets());
            if (nc == null || nc.getPermittedSubtrees() == null) return null;
            List<String> dns = new ArrayList<>();
            for (GeneralSubtree gs : nc.getPermittedSubtrees()) {
                GeneralName gn = gs.getBase();
                if (gn.getTagNo() == GeneralName.dNSName) dns.add(gn.getName().toString());
            }
            return dns.isEmpty() ? null : String.join(", ", dns);
        } catch (RuntimeException e) {
            log.warn("Could not parse NameConstraints from imported cert: {}", e.getMessage());
            return null;
        }
    }

    /** Map the certificate's signature algorithm to a digest name (sha256/sha384/sha512). */
    private String digestFromSigAlg(X509Certificate cert) {
        String a = cert.getSigAlgName() == null ? "" : cert.getSigAlgName().toUpperCase();
        if (a.contains("SHA512")) return "sha512";
        if (a.contains("SHA384")) return "sha384";
        return "sha256";
    }

    /** Certificate lifetime in whole days (notAfter − notBefore), clamped to a sane range. */
    private int validityDays(X509Certificate cert) {
        long days = Duration.between(cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant()).toDays();
        return (int) Math.max(1, Math.min(days, 100_000));
    }

    /** First HTTP(S) CRL Distribution Point URI in the certificate, or null. */
    private String extractCrlUrl(X509Certificate cert) {
        try {
            byte[] ext = cert.getExtensionValue(Extension.cRLDistributionPoints.getId());
            if (ext == null) return null;
            CRLDistPoint dp = CRLDistPoint.getInstance(ASN1OctetString.getInstance(ext).getOctets());
            for (DistributionPoint p : dp.getDistributionPoints()) {
                DistributionPointName dpn = p.getDistributionPoint();
                if (dpn == null || dpn.getType() != DistributionPointName.FULL_NAME) continue;
                for (GeneralName gn : GeneralNames.getInstance(dpn.getName()).getNames()) {
                    if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        String uri = gn.getName().toString();
                        if (uri.startsWith("http")) return uri;
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not parse CRL distribution point from imported cert: {}", e.getMessage());
        }
        return null;
    }

    /** OCSP responder URI from the Authority Information Access extension, or null. */
    private String extractOcspUrl(X509Certificate cert) {
        try {
            byte[] ext = cert.getExtensionValue(Extension.authorityInfoAccess.getId());
            if (ext == null) return null;
            AuthorityInformationAccess aia =
                AuthorityInformationAccess.getInstance(ASN1OctetString.getInstance(ext).getOctets());
            for (AccessDescription ad : aia.getAccessDescriptions()) {
                if (X509ObjectIdentifiers.id_ad_ocsp.equals(ad.getAccessMethod())) {
                    GeneralName loc = ad.getAccessLocation();
                    if (loc.getTagNo() == GeneralName.uniformResourceIdentifier)
                        return loc.getName().toString();
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not parse OCSP URL from imported cert: {}", e.getMessage());
        }
        return null;
    }

    private String toPem(Object obj) throws IOException {
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

    private SubjectKeyIdentifier createSubjectKeyId(PublicKey pub) {
        return new SubjectKeyIdentifier(pub.getEncoded());
    }

    private void applyKeyUsage(X509v3CertificateBuilder b, CertificateRecord.CertType type) throws IOException {
        int usage = switch (type) {
            case SERVER        -> KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
            case CLIENT        -> KeyUsage.digitalSignature | KeyUsage.keyAgreement;
            case CODE_SIGNING  -> KeyUsage.digitalSignature | KeyUsage.nonRepudiation;
            case EMAIL         -> KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.nonRepudiation;
            default            -> KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
        };
        b.addExtension(Extension.keyUsage, true, new KeyUsage(usage));
    }

    private void addSanExtension(X509v3CertificateBuilder b, CertificateRecord cr) throws IOException {
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

    private void addCrlDistPoint(X509v3CertificateBuilder b, String crlUrl) throws IOException {
        GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, crlUrl);
        DistributionPointName dpn = new DistributionPointName(new GeneralNames(gn));
        DistributionPoint dp = new DistributionPoint(dpn, null, null);
        b.addExtension(Extension.cRLDistributionPoints, false,
                new CRLDistPoint(new DistributionPoint[]{dp}));
    }

    private String fingerprint(X509Certificate cert) throws GeneralSecurityException {
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
