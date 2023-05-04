/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
class RbelMessageValidatorReadFileTest {

    @BeforeEach
    @AfterEach
    public void reset() {
        ((Deque<RbelElement>) ReflectionTestUtils.getField(LocalProxyRbelMessageListener.class, "validatableRbelMessages"))
            .clear();
        TigerGlobalConfiguration.reset();
    }

    @Test
    void testReadTrafficFile() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersActive.yaml");
        executeWithSecureShutdown(() -> {
            TigerDirector.start();

            RbelMessageValidator.instance.readTgrFile("src/test/resources/testdata/rezepsFiltered.tgr");

            assertThat(LocalProxyRbelMessageListener.getValidatableRbelMessages()).hasSize(96);
        });
    }

    private void executeWithSecureShutdown(Runnable test) {
        try {
            test.run();
        } finally {
            TigerDirector.getTigerTestEnvMgr().shutDown();
        }
    }
}
