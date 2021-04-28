package de.gematik.test.tiger.lib;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import lombok.SneakyThrows;
import org.junit.Test;

public class TestTigerDirector {

    @Test
    public void testDirectorSimpleIdp() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleidp.yaml");
        TigerDirector.beforeTestRun();

        try {
            Thread.sleep(200000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("TEST DONE");
    }

    @Test
    public void testDirectorIdpAndERezept() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/idpAnderezept.yaml");
        TigerDirector.beforeTestRun();

        try {
            Thread.sleep(200000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("TEST DONE");
    }
}
