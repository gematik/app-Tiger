package de.gematik.test.tiger.lib;

import org.junit.Test;

public class TestTigerDirector {

    //@Test
    public void testDirector() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "../tiger-testenv.yaml");
        TigerDirector.beforeTestRun();
    }
}
