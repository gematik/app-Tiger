package de.gematik.test.tiger.mockserver.socket.tls;

import de.gematik.test.tiger.mockserver.configuration.Configuration;

@FunctionalInterface
public interface KeyAndCertificateFactorySupplier {
  KeyAndCertificateFactory buildKeyAndCertificateFactory(
      boolean isServerInstance, Configuration configuration);
}
