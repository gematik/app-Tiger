package de.gematik.test.tiger.mockserver.socket.tls.bouncycastle;

import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractKeyAndCertificateFactory implements KeyAndCertificateFactory {

  @Override
  public List<X509Certificate> certificateChain() {
    final List<X509Certificate> result = new ArrayList<>();
    result.add(x509Certificate());
    result.add(certificateAuthorityX509Certificate());
    return result;
  }
}
