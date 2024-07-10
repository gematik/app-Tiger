/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.socket.tls;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;

/*
 * @author jamesdbloom
 */
@FunctionalInterface
public interface KeyAndCertificateFactorySupplier {
  KeyAndCertificateFactory buildKeyAndCertificateFactory(
      boolean isServerInstance, MockServerConfiguration configuration);
}
