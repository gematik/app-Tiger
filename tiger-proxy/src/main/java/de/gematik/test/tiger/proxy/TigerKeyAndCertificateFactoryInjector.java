/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import org.mockserver.*;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;

public class TigerKeyAndCertificateFactoryInjector {

    public static void injectIntoMockServer(TigerProxyConfiguration configuration) {
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(
            mockServerLogger -> new TigerKeyAndCertificateFactory(mockServerLogger, configuration));
    }
}
