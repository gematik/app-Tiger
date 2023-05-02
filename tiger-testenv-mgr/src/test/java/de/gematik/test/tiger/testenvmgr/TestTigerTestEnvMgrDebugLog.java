/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.Appender;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.stream.output.TapStream;

class TestTigerTestEnvMgrDebugLog {

    @Test
    void checkDebugOutputIsLogged_OK() throws Exception {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TigerTestEnvMgr.class)).setLevel(Level.DEBUG);
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.initialize();
        TigerGlobalConfiguration.putValue("tiger.localProxyActive", false);
        final String systemOut = tapSystemOut(() -> {
            try (TigerTestEnvMgr tigerTestEnvMgr = new TigerTestEnvMgr()) {
            }
        });
        assertThat(systemOut)
            .contains("TigerTestEnvMgr - Tiger configuration: {")
            .contains("TigerTestEnvMgr - Environment variables: {");

    }

    @Test
    void checkDebugOutputIsNotLoggedOnInfoLevel_OK() throws Exception {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TigerTestEnvMgr.class)).setLevel(Level.INFO);
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.initialize();
        TigerGlobalConfiguration.putValue("tiger.localProxyActive", false);
        final TapStream tapStream = new TapStream();
        try (TigerTestEnvMgr tigerTestEnvMgr = new TigerTestEnvMgr()) {
            assertThat(tapStream.getText())
                .doesNotContain("TigerTestEnvMgr - Tiger configuration: {")
                .doesNotContain("TigerTestEnvMgr - Environment variables: {");
        }
    }
}
