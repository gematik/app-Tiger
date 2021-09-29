/*
 * Copyright (c) 2021 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.testenvmgr.InsecureRestorableTrustAllManager;
import java.net.URL;
import java.net.URLConnection;
import lombok.SneakyThrows;
import org.junit.Test;

public class TestTigerDirector {

    @Test
    public void testBeforeTestRunNOTIGERACTIVE() {
        TigerDirector.testUninitialize();
        System.setProperty("TIGER_ACTIVE", "0");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp.yaml");
        assertThatThrownBy(TigerDirector::beforeTestRun).isInstanceOf(AssertionError.class);
        assertThat(TigerDirector.isInitialized()).isFalse();
        assertThatThrownBy(TigerDirector::getTigerTestEnvMgr).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getProxySettings).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getRbelMessageProvider).isInstanceOf(TigerStartupException.class);
    }

    @Test
    public void testBeforeTestRunIGERACTIVENOTINIT() {
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
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp2.yaml");
        TigerDirector.beforeTestRun();

        assertThat(TigerDirector.isInitialized()).isTrue();
        assertThat(TigerDirector.getTigerTestEnvMgr()).isNotNull();
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy()).isNotNull();
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy().getBaseUrl()).startsWith(
            "http://localhost");
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy().getRbelLogger()).isNotNull();
        // TODO upgrading to testcontainer 1.16.0 causes the ports info to be not available in docker config network bindings
        // so make sure we get ONE valid value here!
        // see https://github.com/testcontainers/testcontainers-java/issues/4489
        assertThat(TigerDirector.getTigerTestEnvMgr().getConfiguration().getServers().get(0).getDockerOptions().getPorts()).hasSize(1);
        assertThat(TigerDirector.getTigerTestEnvMgr().getConfiguration().getServers().get(0).getDockerOptions().getPorts().get(8080)).isNotNull();
    }

    @SneakyThrows
    @Test
    public void testDirectorDisabledProxy() {
        TigerDirector.testUninitialize();
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxydisabled.yaml");
        TigerDirector.beforeTestRun();

        System.out.println("TIGER_ACTIVE " + System.getProperty("TIGER_ACTIVE"));

        System.out.println("PROXY:" + System.getProperty("http.proxyHost") + " / " +  System.getProperty("https.proxyHost"));
        System.out.println("PORTS:" + System.getProperty("http.proxyPort") + " / " +  System.getProperty("https.proxyPort"));

        assertThat(TigerDirector.isInitialized()).isTrue();
        assertThat(TigerDirector.getTigerTestEnvMgr()).isNotNull();
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy()).isNotNull();

        InsecureRestorableTrustAllManager.saveContext();
        InsecureRestorableTrustAllManager.allowAllSSL();
        var url = new URL("http://idp-rise-tu-noproxy");
        URLConnection con = url.openConnection();
        con.setConnectTimeout(1000);
        assertThatThrownBy(con::connect).isInstanceOf(Exception.class);
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
