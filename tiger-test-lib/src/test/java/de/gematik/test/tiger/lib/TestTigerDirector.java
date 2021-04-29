package de.gematik.test.tiger.lib;

import org.junit.Test;

public class TestTigerDirector {

    @Test
    public void testDirectorSimpleIdp() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleidp.yaml");
        TigerDirector.beforeTestRun();
    }

    @Test
    public void testDirectorIdpAndERezept() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/idpAnderezept.yaml");
        //TODO failed 210428 da erezept nicht hochkommt
        TigerDirector.beforeTestRun();
    }
}
