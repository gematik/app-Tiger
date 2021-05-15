package de.gematik.test.tiger.lib;

import org.junit.Test;

public class TestTigerDirector {

    @Test
    public void testDirectorSimpleIdp() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp.yaml");
        TigerDirector.beforeTestRun();
    }

    // tests take too long so skip this for now @Test
    public void testDirectorIdpAndERezept() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/idpAnderezept.yaml");
        TigerDirector.beforeTestRun();
    }
}
