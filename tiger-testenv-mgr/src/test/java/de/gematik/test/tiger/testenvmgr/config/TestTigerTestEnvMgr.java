/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.testenvmgr.HttpsTrustManager;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.List;

import io.swagger.v3.oas.models.Paths;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class TestTigerTestEnvMgr {

    @Test
    public void testCreateShutdownEnv() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpOnly.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("idp");
        srv.setType("docker");
        srv.setSource(List.of("anything......"));
        envMgr.shutDown(srv);
    }

    @Test
    public void testCreateExternalEnv() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/riseIdpOnly.yaml");
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
            return;
        }
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("idp");
        srv.setType("externalUrl");
        srv.setSource(List.of("anything......"));
        envMgr.shutDown(srv);
    }

    @Test
    public void testCreateExternalJarEnv() throws IOException {
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
        assertThatThrownBy(() -> envMgr.setUpEnvironment())
                .isInstanceOf(TigerTestEnvException.class)
                .hasMessage("Process aborted with exit code 1");
    }
    @Test
    public void testCreateInvalidInstanceType() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/invalidInstanceType.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
    }

    @Test
    public void testCreateNonExisitngVersion() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpNonExistingVersion.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
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
