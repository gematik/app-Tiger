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

import static de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.parseInformationString;

import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.TigerPkiIdentityLoaderException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class TigerPkiIdentity {

  private X509Certificate certificate;
  private PrivateKey privateKey;
  @With private Optional<String> keyId = Optional.empty();

  private final List<X509Certificate> certificateChain = new ArrayList<>();

  public TigerPkiIdentity(X509Certificate certificate, PrivateKey privateKey) {
    this.certificate = certificate;
    this.privateKey = privateKey;
    this.keyId = Optional.empty();
  }

  public TigerPkiIdentity(String fileLoadingInformation) {
    this(parseInformationString(fileLoadingInformation));
  }

  public TigerPkiIdentity(TigerPkiIdentityInformation fileLoadingInformation) {
    val identity = TigerPkiIdentityLoader.loadIdentity(fileLoadingInformation);
    setCertificate(identity.getCertificate());
    setPrivateKey(identity.getPrivateKey());
    setKeyId(identity.getKeyId());
    certificateChain.addAll(identity.getCertificateChain());
  }

  public TigerPkiIdentity addCertificateToCertificateChain(X509Certificate newChainCertificate) {
    certificateChain.add(newChainCertificate);
    return this;
  }

  public KeyStore toKeyStoreWithPassword(String password) {
    try {
      final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null);

      final Certificate[] keystoreEntryChain = new Certificate[certificateChain.size() + 1];
      keystoreEntryChain[0] = getCertificate();
      for (int i = 0; i < certificateChain.size(); i++) {
        keystoreEntryChain[i + 1] = certificateChain.get(i);
      }
      final char[] passwordCharArray = password == null ? null : password.toCharArray();
      keyStore.setEntry(
          "entry",
          new PrivateKeyEntry(getPrivateKey(), keystoreEntryChain),
          new PasswordProtection(passwordCharArray));
      return keyStore;
    } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
      throw new TigerPkiIdentityLoaderException("Error while creating keystore", e);
    }
  }

  public List<X509Certificate> buildChainWithCertificate() {
    final List<X509Certificate> certificateWithChain = new ArrayList<>();
    certificateWithChain.add(getCertificate());
    certificateWithChain.addAll(certificateChain);
    return certificateWithChain;
  }
}
