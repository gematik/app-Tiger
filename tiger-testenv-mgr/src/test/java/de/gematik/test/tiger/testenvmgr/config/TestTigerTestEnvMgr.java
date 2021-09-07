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

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
public class TestTigerTestEnvMgr {

    @Test //NOSONAR
    public void testCreateShutdownEnv() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpOnly.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("idp9");
        srv.setType("docker");
        srv.setSource(List.of("anything......"));
        envMgr.shutDown(srv);
    }

    @Test //NOSONAR
    public void testCreateExternalEnv() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/riseIdpOnly.yaml");
        if (!setGematikProxy()) {
            return;
        }
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("idp10");
        srv.setType("externalUrl");
        srv.setSource(List.of("anything......"));
        envMgr.shutDown(srv);
    }

    private boolean setGematikProxy() {
        try {
            URL url = new URL("http://192.168.110.10:3128");
            URLConnection con = url.openConnection();
            con.setConnectTimeout(1000);
            con.connect();
            System.setProperty("http.proxyHost", "192.168.110.10");
            System.setProperty("https.proxyHost", "192.168.110.10");
            System.setProperty("http.proxyPort", "3128");
            System.setProperty("https.proxyPort", "3128");
        } catch (Exception e) {
            // else lets try without internal proxy
            e.printStackTrace();
            System.out.println("Only works with internal gematik proxy! SKIPPED");
            return false;
        }
        return true;
    }

    @SneakyThrows
    @Test
    public void testCreateExternalJarEnv() throws IOException {
        if (!setGematikProxy()) {
            return;
        }
        File f = new File("FdVPortable");
        FileUtils.deleteDirectory(f);
        f.mkdirs();
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/miniJar.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("minijar-test");
        srv.setType("externalJar");
        srv.setSource(List.of("anything......"));
        Thread.sleep(2000);
        envMgr.shutDown(srv);
    }

    @Test
    public void testCreateExternalJarEnvInvalidJar() throws IOException {
        System.setProperty("http.nonProxyHosts", "build.top.local|*.local");
        File f = new File("FdVPortable");
        FileUtils.deleteDirectory(f);
        f.mkdirs();
        f = Path.of("FdVPortable", "download").toFile();
        f.createNewFile();
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/miniJar.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment)
                .isInstanceOf(TigerTestEnvException.class)
                .hasMessage("Unable to start external jar!");
    }

    @Test
    public void testCreateExternalJarRelativePath() throws IOException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/localMiniJar.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("minijar-test-local");
        srv.setType("externalJar");
        srv.setSource(List.of("anything......"));
        envMgr.shutDown(srv);
    }

    @Test
    public void testCreateExternalJarRelativePathFileNotFound() throws IOException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/localMiniJarFileNotFound.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(() -> envMgr.setUpEnvironment()).isInstanceOf(TigerTestEnvException.class)
                .hasMessageStartingWith("Local jar")
                .hasMessageEndingWith("not found!");
    }

    @Test
    public void testCreateInvalidInstanceType() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/invalidInstanceType.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
    }

    @Test
    public void testCreateNonExistingVersion() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpNonExistingVersion.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
    }

    @Test
    public void testCreateUnknwonTemplate() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/unknownTemplate.yaml");
        assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
    }


    //@Test
    public void testCreateEpa2() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(200000);
    }

    //@Test
    public void testCreateEpa2FDV() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa-fdv.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(2000);
    }


    // TODO check pkis set, routings set,....

}
