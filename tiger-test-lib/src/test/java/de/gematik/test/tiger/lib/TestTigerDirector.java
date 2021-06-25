/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.lib.exception.TigerStartupException;
import org.junit.Test;

public class TestTigerDirector {

    @Test public void testBeforeTestRunNOTIGERACTIVE() {
        TigerDirector.testUninitialize();
        System.setProperty("TIGER_ACTIVE", "0");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp.yaml");
        assertThatThrownBy(TigerDirector::beforeTestRun).isInstanceOf(AssertionError.class);
        assertThat(TigerDirector.isInitialized()).isFalse();
        assertThatThrownBy(TigerDirector::getTigerTestEnvMgr).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getProxySettings).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getRbelMessageProvider).isInstanceOf(TigerStartupException.class);
    }

    @Test public void testBeforeTestRunIGERACTIVENOTINIT() {
        TigerDirector.testUninitialize();
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp.yaml");
        assertThat(TigerDirector.isInitialized()).isFalse();
        assertThatThrownBy(TigerDirector::getTigerTestEnvMgr).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getProxySettings).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getRbelMessageProvider).isInstanceOf(TigerStartupException.class);
    }

    @Test
    public void testDirectorSimpleIdp() {
        TigerDirector.testUninitialize();
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp.yaml");
        TigerDirector.beforeTestRun();

        assertThat(TigerDirector.isInitialized()).isTrue();
        assertThat(TigerDirector.getTigerTestEnvMgr()).isNotNull();
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy()).isNotNull();
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getBaseUrl()).startsWith("http://localhost");
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getRbelLogger()).isNotNull();
    }

/*
    public static void synchronizeTestCasesWithPolarion() {
        if (!checkIsInitialized()) {
            return;
        }

        if (OSEnvironment.getAsBoolean("TIGER_SYNC_TESTCASES")) {
            try {
                Method polarionToolBoxMain = Class.forName("de.gematik.polarion.toolbox.ToolBox")
                    .getDeclaredMethod("main", String[].class);
                String[] args = new String[]{"-m", "tcimp", "-dryrun"};
                // TODO read from tiger-testlib.yaml or env vars values for -h -u -p -prj -aq -fd -f -bdd

                log.info("Syncing test cases with Polarion...");
                polarionToolBoxMain.invoke(null, (Object[]) args);
                log.info("Test cases synched with Polarion...");
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                throw new TigerLibraryException("Unable to access Polarion Toolbox! "
                    + "Be sure to have it included in mvn dependencies.", e);
                // TODO add the mvn dependency lines to log output
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new TigerLibraryException("Unable to call Polarion Toolbox's main method!", e);
            }
        }
    }

    public static void beforeTestThreadStart() {
        if (!checkIsInitialized()) {
            return;
        }
        if (proxiesMap.containsKey(tid())) {
            log.warn("Proxy for given thread '" + tid() + "' already initialized!");
            return;
        }
        // instantiate proxy and supply routes and register message provider as listener to proxy
        final var threadProxy = new TigerProxy(tigerTestEnvMgr.getConfiguration().getTigerProxy());
        getTigerTestEnvMgr().getRoutes().forEach(route -> threadProxy.addRoute(route[0], route[1]));
        threadProxy.addRbelMessageListener(rbelMsgProviderMap.computeIfAbsent(tid(), key -> new RbelMessageProvider()));
        proxiesMap.putIfAbsent(tid(), threadProxy);
    }

    public static void createAfoRepoort() {
        if (!checkIsInitialized()) {
            return;
        }
        // TODO create Aforeport and embedd it into serenity report
    }

    private static boolean checkIsInitialized() {
        if (!OSEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.warn("Tiger test environment has not been initialized,"
                + "as the TIGER_ACTIVE environment variable is not set to '1'.");
            return false;
        }
        if (!initialized) {
            throw new AssertionError("Tiger test environment has not been initialized. "
                + "Did you call TigerDirector.beforeTestRun before starting test run?");
        }
        return initialized;
    }
}
     */
}
