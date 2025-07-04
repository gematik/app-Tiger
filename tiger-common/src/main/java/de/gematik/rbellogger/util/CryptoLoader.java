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
package de.gematik.rbellogger.util;

import de.gematik.test.tiger.common.exceptions.TigerPkiException;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Enumeration;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CryptoLoader {

  private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

  public static X509Certificate getCertificateFromP12(final byte[] crt, final String p12Password) {
    try {
      final KeyStore p12 = KeyStore.getInstance("pkcs12", BOUNCY_CASTLE_PROVIDER);
      p12.load(new ByteArrayInputStream(crt), p12Password.toCharArray());
      final Enumeration<String> e = p12.aliases();
      while (e.hasMoreElements()) {
        final String alias = e.nextElement();
        return (X509Certificate) p12.getCertificate(alias);
      }
    } catch (final IOException
        | KeyStoreException
        | NoSuchAlgorithmException
        | CertificateException e) {
      throw new TigerPkiException("Exception while trying to extract certificate from p12!", e);
    }
    throw new TigerPkiException("Could not find certificate in P12-File");
  }

  public static X509Certificate getCertificateFromPem(final byte[] crt) {
    final InputStream in = new ByteArrayInputStream(crt);
    return getCertificateFromPem(in);
  }

  public static X509Certificate getCertificateFromPem(InputStream in) {
    try {
      final CertificateFactory certFactory =
          CertificateFactory.getInstance("X.509", BOUNCY_CASTLE_PROVIDER);
      final X509Certificate x509Certificate = (X509Certificate) certFactory.generateCertificate(in);
      if (x509Certificate == null) {
        throw new TigerPkiException("Error while loading certificate (null)!");
      }
      return x509Certificate;

    } catch (final CertificateException ex) {
      throw new TigerPkiException("Error while loading certificate!", ex);
    }
  }

  public static TigerPkiIdentity getIdentityFromP12(
      final byte[] p12FileContent, final String p12Password) {
    try {
      final KeyStore p12 = KeyStore.getInstance("pkcs12", BOUNCY_CASTLE_PROVIDER);
      p12.load(new ByteArrayInputStream(p12FileContent), p12Password.toCharArray());
      final Enumeration<String> e = p12.aliases();
      while (e.hasMoreElements()) {
        final String alias = e.nextElement();
        final X509Certificate certificate = (X509Certificate) p12.getCertificate(alias);
        final PrivateKey privateKey = (PrivateKey) p12.getKey(alias, p12Password.toCharArray());
        if (privateKey == null) {
          continue;
        }
        return new TigerPkiIdentity(certificate, privateKey, Optional.of(alias));
      }
    } catch (final IOException
        | KeyStoreException
        | NoSuchAlgorithmException
        | UnrecoverableKeyException
        | CertificateException e) {
      throw new TigerPkiException("Exception while trying to extract identity from p12!", e);
    }
    throw new TigerPkiException("Could not find certificate in P12-File");
  }

  public static TigerPkiIdentity getIdentityFromPemAndPkcs8(
      final byte[] certificateData, final byte[] keyBytes) {
    try (final ByteArrayInputStream in = new ByteArrayInputStream(keyBytes);
        final InputStreamReader inputStreamReader = new InputStreamReader(in);
        final PemReader pemReader = new PemReader(inputStreamReader); ) {
      X509Certificate certificate = getCertificateFromPem(certificateData);
      KeyFactory factory = KeyFactory.getInstance(certificate.getPublicKey().getAlgorithm());
      PemObject pemObject = pemReader.readPemObject();
      byte[] content = pemObject.getContent();
      PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
      return TigerPkiIdentity.builder()
          .certificate(certificate)
          .privateKey(factory.generatePrivate(privKeySpec))
          .build();
    } catch (final NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
      throw new TigerPkiException("Exception while trying to extract identity from pkcs8!", e);
    }
  }

  public static TigerPkiIdentity getIdentityFromPemAndPkcs1(
      final byte[] certificateData, final byte[] keyBytes) {
    try (final ByteArrayInputStream in = new ByteArrayInputStream(keyBytes);
        final InputStreamReader inputStreamReader = new InputStreamReader(in);
        final PEMParser pemParser = new PEMParser(inputStreamReader)) {
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BOUNCY_CASTLE_PROVIDER);
      Object object = pemParser.readObject();
      KeyPair keyPair = converter.getKeyPair((PEMKeyPair) object);

      X509Certificate certificate = getCertificateFromPem(certificateData);
      return TigerPkiIdentity.builder()
          .certificate(certificate)
          .privateKey(keyPair.getPrivate())
          .build();
    } catch (final IOException e) {
      throw new TigerPkiException("Exception while trying to extract identity from pkcs1!", e);
    }
  }
}
