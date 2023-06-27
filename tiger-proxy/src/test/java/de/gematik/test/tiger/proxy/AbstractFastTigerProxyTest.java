/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class AbstractFastTigerProxyTest extends AbstractTigerProxyTest {

    @BeforeAll
    public void setupTigerProxy() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .build());
    }

    @BeforeEach
    public void cleanupTiger() {
        tigerProxy.clearAllRoutes();
        tigerProxy.clearAllMessages();
    }

    @Override
    public void stopSpawnedTigerProxy() {
        // nothing
    }
}
