package de.gematik.test.tiger.mockserver.socket.tls;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

public interface KeyAndCertificateFactory {

  void buildAndSavePrivateKeyAndX509Certificate();

  boolean certificateNotYetCreated();

  PrivateKey privateKey();

  X509Certificate x509Certificate();

  X509Certificate certificateAuthorityX509Certificate();

  List<X509Certificate> certificateChain();
}
