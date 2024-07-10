/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.pki;

import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.TigerPkiIdentityLoaderException;
import java.io.File;
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
    final TigerPkiIdentity identity =
        TigerPkiIdentityLoader.loadRbelPkiIdentity(fileLoadingInformation);
    setCertificate(identity.getCertificate());
    setPrivateKey(identity.getPrivateKey());
    setKeyId(identity.getKeyId());
    certificateChain.addAll(identity.getCertificateChain());
  }

  public TigerPkiIdentity(File file, String fileLoadingInformation) {
    final TigerPkiIdentity identity =
        TigerPkiIdentityLoader.loadRbelPkiIdentity(file, fileLoadingInformation);
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
}
