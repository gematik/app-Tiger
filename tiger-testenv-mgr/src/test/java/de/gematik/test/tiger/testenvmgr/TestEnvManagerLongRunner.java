/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
public class TestEnvManagerLongRunner extends AbstractTestTigerTestEnvMgr {

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateEpa2() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa.yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            try {
                Thread.sleep(200000);
            } catch (InterruptedException e) {
            }
        });
    }

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateDemis() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDemis.yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            try {
                Thread.sleep(20000000);
            } catch (InterruptedException e) {
            }
        });
    }

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateEpa2FDV() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa-fdv.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(2000);
    }
}
