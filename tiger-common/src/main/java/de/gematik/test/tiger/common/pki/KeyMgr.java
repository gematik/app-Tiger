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
package de.gematik.test.tiger.common.pki;

import de.gematik.test.tiger.common.exceptions.TigerPkiException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class KeyMgr {

  private static final String BEGINPUBKEY_STR = "-----BEGIN PUBLIC KEY-----";

  private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

  private KeyMgr() {}

  public static Key readKeyFromPem(String pem) {
    if (pem.contains(BEGINPUBKEY_STR)) {
      throw new NotImplementedException(
          "Future me - Public keys from PEM is currently not implemented!");
    } else {
      return readPrivateKeyFromPem(pem);
    }
  }

  @SneakyThrows
  public static Certificate readCertificateFromPem(final String pem) {
    var certFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE_PROVIDER);
    final InputStream in = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
    return certFactory.generateCertificate(in);
  }

  @SneakyThrows
  public static Key readPrivateKeyFromPem(String pem) {
    var pemParser = new PEMParser(new StringReader(pem));
    var converter = new JcaPEMKeyConverter();
    return converter.getPrivateKey(PrivateKeyInfo.getInstance(pemParser.readObject()));
  }

  public static KeyPair readEcdsaKeypairFromPkcs8Pem(byte[] pemContent) {
    try (final ByteArrayInputStream in = new ByteArrayInputStream(pemContent);
        final InputStreamReader inputStreamReader = new InputStreamReader(in);
        final PemReader pemReader = new PemReader(inputStreamReader)) {
      KeyFactory factory = KeyFactory.getInstance("ECDSA", BOUNCY_CASTLE_PROVIDER);
      PemObject pemObject = pemReader.readPemObject();
      byte[] content = pemObject.getContent();
      PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
      final BCECPrivateKey privateKey = (BCECPrivateKey) factory.generatePrivate(privKeySpec);
      KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BOUNCY_CASTLE_PROVIDER);

      ECParameterSpec ecSpec = privateKey.getParameters();
      ECPoint q = ecSpec.getG().multiply(privateKey.getD());

      ECPublicKeySpec pubSpec = new ECPublicKeySpec(q, ecSpec);
      return new KeyPair(keyFactory.generatePublic(pubSpec), privateKey);
    } catch (final NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
      throw new TigerPkiException("Unable to read key pair from Pkcs8 pem data", e);
    }
  }
}
