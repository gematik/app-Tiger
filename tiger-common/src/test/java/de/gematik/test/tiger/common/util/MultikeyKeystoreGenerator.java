/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.common.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import lombok.SneakyThrows;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Utility to generate an enhanced multikey.p12 keystore with certificate chains. This adds
 * chainAlias1 and chainAlias2 entries containing complete certificate chains to the existing
 * multikey keystore structure.
 */
public class MultikeyKeystoreGenerator {

  // Use absolute path to ensure we're saving in the correct tiger-common location
  private static final String KEYSTORE_PATH = "tiger-common/src/test/resources/multikey.p12";
  private static final String KEYSTORE_PASSWORD = "gematik";
  private static final String KEYSTORE_TYPE = "PKCS12";

  @SneakyThrows
  public static void main(String[] args) {
    Security.addProvider(new BouncyCastleProvider());

    if (args.length > 0 && "regenerateComplete".equals(args[0])) {
      regenerateCompleteKeystore();
      return;
    }

    System.out.println("=== Enhanced Multikey Keystore Generator ===\n");

    // Load existing keystore or create new one
    KeyStore keystore = loadOrCreateKeystore();

    // Add the certificate chains using the simplified approach
    addCertificateChains(keystore);

    // Save the enhanced keystore
    saveKeystore(keystore);

    // Display keystore contents
    System.out.println("\n3. Enhanced keystore contents:");
    displayKeystoreContents(keystore);

    System.out.println("\n=== Enhanced multikey.p12 keystore generated successfully! ===");
    System.out.println("Location: " + KEYSTORE_PATH);
    System.out.println("Password: " + KEYSTORE_PASSWORD);
  }

  /** Validates that the certificate chain is properly constructed and valid */
  @SneakyThrows
  private static void validateCertificateChain(CertificateChain chain, String chainName) {
    System.out.println("  Validating certificate chain for " + chainName + "...");

    X509Certificate[] certChain = chain.getCertificateChain();

    // Basic chain structure validation
    if (certChain.length < 2) {
      throw new IllegalStateException(
          "Certificate chain should have at least 2 certificates (end entity, intermediate/root)");
    }

    // Validate that end entity is signed by intermediate
    try {
      certChain[0].verify(certChain[1].getPublicKey());
      System.out.println("    ✓ End entity certificate verified against intermediate CA");
    } catch (Exception e) {
      throw new IllegalStateException(
          "End entity certificate is not properly signed by intermediate CA", e);
    }

    // Validate that intermediate is signed by root
    try {
      certChain[1].verify(certChain[2].getPublicKey());
      System.out.println("    ✓ Intermediate CA certificate verified against root CA");
    } catch (Exception e) {
      throw new IllegalStateException(
          "Intermediate CA certificate is not properly signed by root CA", e);
    }

    // Validate that root is self-signed
    try {
      certChain[2].verify(certChain[2].getPublicKey());
      System.out.println("    ✓ Root CA certificate is properly self-signed");
    } catch (Exception e) {
      throw new IllegalStateException("Root CA certificate is not properly self-signed", e);
    }

    // Validate certificate validity periods
    java.util.Date now = new java.util.Date();
    for (int i = 0; i < certChain.length; i++) {
      try {
        certChain[i].checkValidity(now);
        System.out.println("    ✓ Certificate " + i + " is within validity period");
      } catch (Exception e) {
        throw new IllegalStateException("Certificate " + i + " is not valid at current time", e);
      }
    }

    System.out.println("    ✓ Certificate chain validation completed successfully");
  }

  @SneakyThrows
  private static KeyStore loadOrCreateKeystore() {
    // Use BouncyCastle PKCS12 implementation explicitly, matching TigerPkiIdentityLoader
    de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser.initialize();
    KeyStore keystore = KeyStore.getInstance(KEYSTORE_TYPE, "BC");

    try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH)) {
      keystore.load(fis, KEYSTORE_PASSWORD.toCharArray());
      System.out.println("✓ Existing keystore loaded from: " + KEYSTORE_PATH);
    } catch (Exception e) {
      // Create new keystore if file doesn't exist
      keystore.load(null, null);
      System.out.println("✓ New keystore created");
    }

    return keystore;
  }

  @SneakyThrows
  private static void addCertificateChainToKeystore(
      KeyStore keystore, String alias, CertificateChain chain) {

    // Create certificate chain array (end entity -> intermediate -> root)
    X509Certificate[] certChain = chain.getCertificateChain();

    // Debug: Print certificate chain details
    System.out.println("    Adding certificate chain to keystore:");
    for (int i = 0; i < certChain.length; i++) {
      System.out.println("      [" + i + "] " + certChain[i].getSubjectX500Principal().getName());
      System.out.println("          Issued by: " + certChain[i].getIssuerX500Principal().getName());
    }

    // Debug: Print the full chain array before writing to keystore
    System.out.println("    [DEBUG] Chain array for alias: " + alias);
    for (int i = 0; i < certChain.length; i++) {
      System.out.println(
          "      Chain[" + i + "]: " + certChain[i].getSubjectX500Principal().getName());
    }

    // Validate the chain using Java's built-in path validation
    try {
      java.security.cert.CertPathValidator pathValidator =
          java.security.cert.CertPathValidator.getInstance("PKIX");
      java.security.cert.CertificateFactory certFactory =
          java.security.cert.CertificateFactory.getInstance("X.509");

      // Create a certificate path from the chain (excluding root for path validation)
      java.util.List<java.security.cert.Certificate> certList =
          java.util.Arrays.asList(certChain[0], certChain[1]); // end entity + intermediate
      java.security.cert.CertPath certPath = certFactory.generateCertPath(certList);

      // Create trust anchor from root CA
      java.security.cert.TrustAnchor trustAnchor =
          new java.security.cert.TrustAnchor(certChain[2], null);
      java.util.Set<java.security.cert.TrustAnchor> trustAnchors =
          java.util.Collections.singleton(trustAnchor);

      // Set up path validation parameters
      java.security.cert.PKIXParameters params =
          new java.security.cert.PKIXParameters(trustAnchors);
      params.setRevocationEnabled(false); // Disable revocation checking for test certificates

      // Validate the path
      pathValidator.validate(certPath, params);
      System.out.println("    ✓ Certificate path validation successful");
    } catch (Exception e) {
      System.out.println("    ! Certificate path validation failed: " + e.getMessage());
      System.out.println("    ! Attempting to add anyway for testing purposes...");
    }

    // Ensure chain length > 1 before adding
    if (certChain.length <= 1) {
      throw new IllegalArgumentException(
          "Certificate chain for alias '"
              + alias
              + "' must have length greater than 1, but was "
              + certChain.length);
    }
    assert certChain.length > 1
        : "Generated certificate chain for alias '" + alias + "' must have length greater than 1";

    // Add the private key and certificate chain to keystore
    // For PKCS12, we need to ensure the chain is properly trusted
    keystore.setKeyEntry(
        alias,
        chain.getEndEntityKeyPair().getPrivate(),
        KEYSTORE_PASSWORD.toCharArray(),
        certChain);
    System.out.println("    ✓ Certificate chain successfully added to keystore");
  }

  @SneakyThrows
  private static void saveKeystore(KeyStore keystore) {
    // Ensure parent directory exists
    java.io.File keystoreFile = new java.io.File(KEYSTORE_PATH);
    java.io.File parentDir = keystoreFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      boolean created = parentDir.mkdirs();
      if (!created) {
        System.out.println("Warning: Could not create parent directory: " + parentDir);
      }
    }

    try (FileOutputStream fos = new FileOutputStream(KEYSTORE_PATH)) {
      keystore.store(fos, KEYSTORE_PASSWORD.toCharArray());
    }
    System.out.println("✓ Keystore saved to: " + KEYSTORE_PATH);
  }

  @SneakyThrows
  private static void displayKeystoreContents(KeyStore keystore) {
    System.out.println("Aliases in keystore:");
    var aliases = keystore.aliases();
    int count = 0;
    while (aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      count++;

      try {
        if (keystore.isKeyEntry(alias)) {
          X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
          java.security.cert.Certificate[] certArray = keystore.getCertificateChain(alias);

          System.out.println("  " + count + ". " + alias + " (private key entry)");
          System.out.println("     Subject: " + cert.getSubjectX500Principal().getName());
          System.out.println("     Issuer: " + cert.getIssuerX500Principal().getName());
          System.out.println("     Chain length: " + (certArray != null ? certArray.length : 1));
          System.out.println("     Valid until: " + cert.getNotAfter());

        } else if (keystore.isCertificateEntry(alias)) {
          X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
          System.out.println("  " + count + ". " + alias + " (certificate entry)");
          System.out.println("     Subject: " + cert.getSubjectX500Principal().getName());
        }
      } catch (Exception e) {
        System.out.println(
            "  " + count + ". " + alias + " (error reading details: " + e.getMessage() + ")");
      }
    }

    if (count == 0) {
      System.out.println("  (no entries found)");
    }
  }

  /** Method to regenerate the entire keystore with both original keys and new chains */
  @SneakyThrows
  public static void regenerateCompleteKeystore() {
    System.out.println("=== Regenerating Complete Multikey Keystore ===\n");

    // Create fresh keystore
    KeyStore keystore = KeyStore.getInstance(KEYSTORE_TYPE);
    keystore.load(null, null);

    // Add original self-signed certificates (key1, key2, key3)
    addOriginalKeys(keystore);

    // Add new certificate chains
    addCertificateChains(keystore);

    // Save keystore
    saveKeystore(keystore);
    displayKeystoreContents(keystore);

    System.out.println("\n=== Complete keystore regenerated successfully! ===");
  }

  @SneakyThrows
  private static void addOriginalKeys(KeyStore keystore) {
    System.out.println("Adding original self-signed certificates...");

    // Generate original key1, key2, key3 as self-signed certificates
    for (int i = 1; i <= 3; i++) {
      String alias = "key" + i;
      String subject =
          String.format(
              "CN=Test Key %d, OU=Test Unit %d, O=Test Org %d, L=Test City %d, ST=Test State %d,"
                  + " C=US",
              i, i, i, i, i);

      SelfSignedCertificate selfSigned = generateSelfSignedCertificate(subject, 365);

      keystore.setKeyEntry(
          alias,
          selfSigned.getKeyPair().getPrivate(),
          KEYSTORE_PASSWORD.toCharArray(),
          new X509Certificate[] {selfSigned.getCertificate()});

      System.out.println("✓ " + alias + " added");
    }
  }

  @SneakyThrows
  private static void addCertificateChains(KeyStore keystore) {
    System.out.println("Adding certificate chains...");

    // Create chainalias1 with 3-certificate chain: [end entity, intermediate CA, root CA]
    System.out.println("  Creating chainalias1 with 3-certificate chain...");
    java.security.KeyPair rootKeyPair1 = generateKeyPair();
    X509Certificate rootCert1 =
        createSimpleCACertificate(rootKeyPair1, "CN=Tiger Root CA 1, O=Tiger Test Framework, C=DE");

    java.security.KeyPair intermediateKeyPair1 = generateKeyPair();
    X509Certificate intermediateCert1 =
        createIntermediateCACertificate(
            intermediateKeyPair1,
            rootKeyPair1.getPrivate(),
            rootCert1,
            "CN=Tiger Intermediate CA 1, O=Tiger Test Framework, C=DE");

    java.security.KeyPair endKeyPair1 = generateKeyPair();
    X509Certificate endCert1 =
        createSimpleEndEntityCertificate(
            endKeyPair1,
            intermediateKeyPair1.getPrivate(),
            intermediateCert1,
            "CN=chainalias1.tiger.local, O=Tiger Test Framework Chain1, C=DE");

    // --- Explicitly check and print certificate details for debugging ---
    System.out.println("  chainalias1 certificate subjects and issuers:");
    System.out.println("    End-entity Subject: " + endCert1.getSubjectX500Principal());
    System.out.println("    End-entity Issuer:  " + endCert1.getIssuerX500Principal());
    System.out.println("    Intermediate Subject: " + intermediateCert1.getSubjectX500Principal());
    System.out.println("    Intermediate Issuer:  " + intermediateCert1.getIssuerX500Principal());
    System.out.println("    Root Subject: " + rootCert1.getSubjectX500Principal());
    System.out.println("    Root Issuer:  " + rootCert1.getIssuerX500Principal());
    System.out.println("    End-entity CA: " + endCert1.getBasicConstraints());
    System.out.println("    Intermediate CA: " + intermediateCert1.getBasicConstraints());
    System.out.println("    Root CA: " + rootCert1.getBasicConstraints());
    // --- End debug ---

    // --- Print full certificate details for debugging ---
    System.out.println("  chainalias1 certificate details:");
    for (X509Certificate cert : new X509Certificate[] {endCert1, intermediateCert1, rootCert1}) {
      System.out.println("    Subject: " + cert.getSubjectX500Principal());
      System.out.println("    Issuer:  " + cert.getIssuerX500Principal());
      System.out.println("    BasicConstraints: " + cert.getBasicConstraints());
      System.out.println("    Is CA: " + (cert.getBasicConstraints() != -1));
      System.out.println("    Signature Algorithm: " + cert.getSigAlgName());
      System.out.println("    Serial: " + cert.getSerialNumber());
      System.out.println("    Valid from: " + cert.getNotBefore() + " to " + cert.getNotAfter());
      System.out.println("    ----------------------------------------");
    }
    // --- End debug ---

    X509Certificate[] chain1 = new X509Certificate[] {endCert1, intermediateCert1, rootCert1};
    validateCertificateChain(
        new CertificateChain(
            rootCert1, rootKeyPair1,
            intermediateCert1, intermediateKeyPair1,
            endCert1, endKeyPair1),
        "chainalias1");
    keystore.setKeyEntry(
        "chainalias1", endKeyPair1.getPrivate(), KEYSTORE_PASSWORD.toCharArray(), chain1);
    System.out.println("    ��� chainalias1 added with chain length: " + chain1.length);

    // Add root CA as a trusted certificate entry for chainalias1
    keystore.setCertificateEntry("chainalias1-root", rootCert1);
    System.out.println("    ✓ chainalias1-root added as trusted certificate entry");

    // For key entry, use only [end-entity, intermediate CA] (exclude root CA)
    X509Certificate[] chain1NoRoot = new X509Certificate[] {endCert1, intermediateCert1};
    keystore.setKeyEntry(
        "chainalias1-noroot",
        endKeyPair1.getPrivate(),
        KEYSTORE_PASSWORD.toCharArray(),
        chain1NoRoot);
    System.out.println("    ✓ chainalias1-noroot added with chain length: " + chain1NoRoot.length);

    // Create chainalias2 with 3-certificate chain: [end entity, intermediate CA, root CA]
    System.out.println("  Creating chainalias2 with 3-certificate chain...");
    java.security.KeyPair rootKeyPair2 = generateKeyPair();
    X509Certificate rootCert2 =
        createSimpleCACertificate(rootKeyPair2, "CN=Tiger Root CA 2, O=Tiger Test Services, C=US");
    java.security.KeyPair intermediateKeyPair2 = generateKeyPair();
    X509Certificate intermediateCert2 =
        createIntermediateCACertificate(
            intermediateKeyPair2,
            rootKeyPair2.getPrivate(),
            rootCert2,
            "CN=Tiger Intermediate CA 2, O=Tiger Test Services, C=US");
    java.security.KeyPair endKeyPair2 = generateKeyPair();
    X509Certificate endCert2 =
        createSimpleEndEntityCertificate(
            endKeyPair2,
            intermediateKeyPair2.getPrivate(),
            intermediateCert2,
            "CN=chainalias2.tiger.test, O=Tiger Test Services Chain2, C=US");
    // --- Explicitly check and print certificate details for debugging ---
    System.out.println("  chainalias2 certificate subjects and issuers:");
    System.out.println("    End-entity Subject: " + endCert2.getSubjectX500Principal());
    System.out.println("    End-entity Issuer:  " + endCert2.getIssuerX500Principal());
    System.out.println("    Intermediate Subject: " + intermediateCert2.getSubjectX500Principal());
    System.out.println("    Intermediate Issuer:  " + intermediateCert2.getIssuerX500Principal());
    System.out.println("    Root Subject: " + rootCert2.getSubjectX500Principal());
    System.out.println("    Root Issuer:  " + rootCert2.getIssuerX500Principal());
    System.out.println("    End-entity CA: " + endCert2.getBasicConstraints());
    System.out.println("    Intermediate CA: " + intermediateCert2.getBasicConstraints());
    System.out.println("    Root CA: " + rootCert2.getBasicConstraints());
    // --- End debug ---

    // --- Print full certificate details for debugging ---
    System.out.println("  chainalias2 certificate details:");
    for (X509Certificate cert : new X509Certificate[] {endCert2, intermediateCert2, rootCert2}) {
      System.out.println("    Subject: " + cert.getSubjectX500Principal());
      System.out.println("    Issuer:  " + cert.getIssuerX500Principal());
      System.out.println("    BasicConstraints: " + cert.getBasicConstraints());
      System.out.println("    Is CA: " + (cert.getBasicConstraints() != -1));
      System.out.println("    Signature Algorithm: " + cert.getSigAlgName());
      System.out.println("    Serial: " + cert.getSerialNumber());
      System.out.println("    Valid from: " + cert.getNotBefore() + " to " + cert.getNotAfter());
      System.out.println("    ----------------------------------------");
    }
    // --- End debug ---

    X509Certificate[] chain2 = new X509Certificate[] {endCert2, intermediateCert2, rootCert2};
    validateCertificateChain(
        new CertificateChain(
            rootCert2, rootKeyPair2,
            intermediateCert2, intermediateKeyPair2,
            endCert2, endKeyPair2),
        "chainalias2");
    keystore.setKeyEntry(
        "chainalias2", endKeyPair2.getPrivate(), KEYSTORE_PASSWORD.toCharArray(), chain2);
    System.out.println("    ✓ chainalias2 added with chain length: " + chain2.length);

    // Add root CA as a trusted certificate entry for chainalias2
    keystore.setCertificateEntry("chainalias2-root", rootCert2);
    System.out.println("    ✓ chainalias2-root added as trusted certificate entry");

    // For key entry, use only [end-entity, intermediate CA] (exclude root CA)
    X509Certificate[] chain2NoRoot = new X509Certificate[] {endCert2, intermediateCert2};
    keystore.setKeyEntry(
        "chainalias2-noroot",
        endKeyPair2.getPrivate(),
        KEYSTORE_PASSWORD.toCharArray(),
        chain2NoRoot);
    System.out.println("    ✓ chainalias2-noroot added with chain length: " + chain2NoRoot.length);
  }

  @SneakyThrows
  private static void validateSimpleChain(X509Certificate[] chain, String chainName) {
    System.out.println("    Validating certificate chain for " + chainName + "...");

    if (chain.length != 2) {
      throw new IllegalStateException("Expected chain length of 2, got: " + chain.length);
    }

    // Verify end entity is signed by CA
    try {
      chain[0].verify(chain[1].getPublicKey());
      System.out.println("      ✓ End entity verified against CA");
    } catch (Exception e) {
      throw new IllegalStateException("End entity not properly signed by CA", e);
    }

    // Verify CA is self-signed
    try {
      chain[1].verify(chain[1].getPublicKey());
      System.out.println("      ✓ CA is self-signed");
    } catch (Exception e) {
      throw new IllegalStateException("CA not properly self-signed", e);
    }

    System.out.println("      ✓ Certificate chain validation successful");
  }

  @SneakyThrows
  private static X509Certificate createSimpleCACertificate(
      java.security.KeyPair keyPair, String subject) {

    org.bouncycastle.asn1.x500.X500Name subjectName =
        new org.bouncycastle.asn1.x500.X500Name(subject);
    java.math.BigInteger serialNumber = java.math.BigInteger.valueOf(System.currentTimeMillis());
    java.util.Date notBefore = new java.util.Date();
    java.util.Date notAfter =
        java.util.Date.from(
            java.time.LocalDateTime.now().plusDays(365).toInstant(java.time.ZoneOffset.UTC));

    org.bouncycastle.cert.X509v3CertificateBuilder certBuilder =
        new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            subjectName, serialNumber, notBefore, notAfter, subjectName, keyPair.getPublic());

    // Only add basic constraints - no other extensions to avoid validation issues
    certBuilder.addExtension(
        org.bouncycastle.asn1.x509.Extension.basicConstraints,
        true,
        new org.bouncycastle.asn1.x509.BasicConstraints(0)); // CA=true, pathLen=0
    certBuilder.addExtension(
        org.bouncycastle.asn1.x509.Extension.keyUsage,
        true,
        new org.bouncycastle.asn1.x509.KeyUsage(
            org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
                | org.bouncycastle.asn1.x509.KeyUsage.cRLSign));

    org.bouncycastle.operator.ContentSigner signer =
        new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA")
            .build(keyPair.getPrivate());

    org.bouncycastle.cert.X509CertificateHolder certHolder = certBuilder.build(signer);
    return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
        .getCertificate(certHolder);
  }

  @SneakyThrows
  private static X509Certificate createIntermediateCACertificate(
      java.security.KeyPair keyPair,
      java.security.PrivateKey issuerKey,
      X509Certificate issuerCert,
      String subject) {

    org.bouncycastle.asn1.x500.X500Name issuerName =
        new org.bouncycastle.asn1.x500.X500Name(issuerCert.getSubjectX500Principal().getName());
    org.bouncycastle.asn1.x500.X500Name subjectName =
        new org.bouncycastle.asn1.x500.X500Name(subject);
    java.math.BigInteger serialNumber =
        java.math.BigInteger.valueOf(System.currentTimeMillis() + 1);
    java.util.Date notBefore = new java.util.Date();
    java.util.Date notAfter =
        java.util.Date.from(
            java.time.LocalDateTime.now().plusDays(365).toInstant(java.time.ZoneOffset.UTC));

    org.bouncycastle.cert.X509v3CertificateBuilder certBuilder =
        new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            issuerName, serialNumber, notBefore, notAfter, subjectName, keyPair.getPublic());

    // Only add basic constraints - no other extensions to avoid validation issues
    certBuilder.addExtension(
        org.bouncycastle.asn1.x509.Extension.basicConstraints,
        true,
        new org.bouncycastle.asn1.x509.BasicConstraints(0)); // CA=true, pathLen=0
    certBuilder.addExtension(
        org.bouncycastle.asn1.x509.Extension.keyUsage,
        true,
        new org.bouncycastle.asn1.x509.KeyUsage(
            org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
                | org.bouncycastle.asn1.x509.KeyUsage.cRLSign));

    org.bouncycastle.operator.ContentSigner signer =
        new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA")
            .build(issuerKey);

    org.bouncycastle.cert.X509CertificateHolder certHolder = certBuilder.build(signer);
    return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
        .getCertificate(certHolder);
  }

  @SneakyThrows
  private static X509Certificate createSimpleEndEntityCertificate(
      java.security.KeyPair keyPair,
      java.security.PrivateKey issuerKey,
      X509Certificate issuerCert,
      String subject) {

    org.bouncycastle.asn1.x500.X500Name issuerName =
        new org.bouncycastle.asn1.x500.X500Name(issuerCert.getSubjectX500Principal().getName());
    org.bouncycastle.asn1.x500.X500Name subjectName =
        new org.bouncycastle.asn1.x500.X500Name(subject);
    java.math.BigInteger serialNumber =
        java.math.BigInteger.valueOf(System.currentTimeMillis() + 1);
    java.util.Date notBefore = new java.util.Date();
    java.util.Date notAfter =
        java.util.Date.from(
            java.time.LocalDateTime.now().plusDays(365).toInstant(java.time.ZoneOffset.UTC));

    org.bouncycastle.cert.X509v3CertificateBuilder certBuilder =
        new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            issuerName, serialNumber, notBefore, notAfter, subjectName, keyPair.getPublic());

    // Only add basic constraints - no other extensions to avoid validation issues
    certBuilder.addExtension(
        org.bouncycastle.asn1.x509.Extension.basicConstraints,
        true,
        new org.bouncycastle.asn1.x509.BasicConstraints(false)); // CA=false

    org.bouncycastle.operator.ContentSigner signer =
        new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA")
            .build(issuerKey);

    org.bouncycastle.cert.X509CertificateHolder certHolder = certBuilder.build(signer);
    return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
        .getCertificate(certHolder);
  }

  @SneakyThrows
  private static java.security.KeyPair generateKeyPair() {
    java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA", "BC");
    keyGen.initialize(2048);
    return keyGen.generateKeyPair();
  }

  /**
   * Simple POJO to hold a certificate chain and associated key pairs for validation and keystore
   * operations.
   */
  public static class CertificateChain {
    private final X509Certificate rootCert;
    private final java.security.KeyPair rootKeyPair;
    private final X509Certificate intermediateCert;
    private final java.security.KeyPair intermediateKeyPair;
    private final X509Certificate endEntityCert;
    private final java.security.KeyPair endEntityKeyPair;

    public CertificateChain(
        X509Certificate rootCert,
        java.security.KeyPair rootKeyPair,
        X509Certificate intermediateCert,
        java.security.KeyPair intermediateKeyPair,
        X509Certificate endEntityCert,
        java.security.KeyPair endEntityKeyPair) {
      this.rootCert = rootCert;
      this.rootKeyPair = rootKeyPair;
      this.intermediateCert = intermediateCert;
      this.intermediateKeyPair = intermediateKeyPair;
      this.endEntityCert = endEntityCert;
      this.endEntityKeyPair = endEntityKeyPair;
    }

    public X509Certificate[] getCertificateChain() {
      return new X509Certificate[] {endEntityCert, intermediateCert, rootCert};
    }

    public X509Certificate getRootCert() {
      return rootCert;
    }

    public java.security.KeyPair getRootKeyPair() {
      return rootKeyPair;
    }

    public X509Certificate getIntermediateCert() {
      return intermediateCert;
    }

    public java.security.KeyPair getIntermediateKeyPair() {
      return intermediateKeyPair;
    }

    public X509Certificate getEndEntityCert() {
      return endEntityCert;
    }

    public java.security.KeyPair getEndEntityKeyPair() {
      return endEntityKeyPair;
    }
  }

  /** Represents a self-signed certificate and its key pair. */
  public static class SelfSignedCertificate {
    private final X509Certificate certificate;
    private final java.security.KeyPair keyPair;

    public SelfSignedCertificate(X509Certificate certificate, java.security.KeyPair keyPair) {
      this.certificate = certificate;
      this.keyPair = keyPair;
    }

    public X509Certificate getCertificate() {
      return certificate;
    }

    public java.security.KeyPair getKeyPair() {
      return keyPair;
    }
  }

  /** Generates a self-signed certificate for the given subject and validity period. */
  @SneakyThrows
  public static SelfSignedCertificate generateSelfSignedCertificate(
      String subject, int validityDays) {
    java.security.KeyPair keyPair = generateKeyPair();
    org.bouncycastle.asn1.x500.X500Name subjectName =
        new org.bouncycastle.asn1.x500.X500Name(subject);
    java.math.BigInteger serialNumber = java.math.BigInteger.valueOf(System.currentTimeMillis());
    java.util.Date notBefore = new java.util.Date();
    java.util.Date notAfter =
        java.util.Date.from(
            java.time.LocalDateTime.now()
                .plusDays(validityDays)
                .toInstant(java.time.ZoneOffset.UTC));
    org.bouncycastle.cert.X509v3CertificateBuilder certBuilder =
        new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            subjectName, serialNumber, notBefore, notAfter, subjectName, keyPair.getPublic());
    certBuilder.addExtension(
        org.bouncycastle.asn1.x509.Extension.basicConstraints,
        true,
        new org.bouncycastle.asn1.x509.BasicConstraints(true));
    certBuilder.addExtension(
        org.bouncycastle.asn1.x509.Extension.keyUsage,
        true,
        new org.bouncycastle.asn1.x509.KeyUsage(
            org.bouncycastle.asn1.x509.KeyUsage.digitalSignature
                | org.bouncycastle.asn1.x509.KeyUsage.keyCertSign
                | org.bouncycastle.asn1.x509.KeyUsage.cRLSign));
    org.bouncycastle.operator.ContentSigner signer =
        new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA")
            .build(keyPair.getPrivate());
    org.bouncycastle.cert.X509CertificateHolder certHolder = certBuilder.build(signer);
    X509Certificate cert =
        new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(certHolder);
    return new SelfSignedCertificate(cert, keyPair);
  }
}
