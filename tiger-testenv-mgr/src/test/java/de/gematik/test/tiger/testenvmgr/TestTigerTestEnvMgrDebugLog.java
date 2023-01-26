/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Level;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TestTigerTestEnvMgrDebugLog {

    @Test
    void checkDebugOutputIsLogged_OK() throws Exception {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TigerTestEnvMgr.class)).setLevel(Level.DEBUG);
        TigerGlobalConfiguration.initialize();
        TigerGlobalConfiguration.putValue("tiger.localProxyActive", false);
        assertThat(tapSystemOut(TigerTestEnvMgr::new))
            .contains("TigerTestEnvMgr - Tiger configuration: {")
            .contains("TigerTestEnvMgr - Environment variables: {");
    }

    @Test
    void checkDebugOutputIsNotLoggedOnInfoLevel_OK() throws Exception {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TigerTestEnvMgr.class)).setLevel(Level.INFO);
        TigerGlobalConfiguration.initialize();
        TigerGlobalConfiguration.putValue("tiger.localProxyActive", false);
        assertThat(tapSystemOut(TigerTestEnvMgr::new))
            .doesNotContain("TigerTestEnvMgr - Tiger configuration: {")
            .doesNotContain("TigerTestEnvMgr - Environment variables: {");
    }

}
