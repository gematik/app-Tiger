package de.gematik.test.tiger.lib;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import lombok.SneakyThrows;
import org.junit.Test;

public class TestTigerDirector {

    @SneakyThrows
    @Test
    public void testDirector() {
        System.setProperty("TIGER_ACTIVE", "1");
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleidp.yaml");
        TigerDirector.beforeTestRun();


        ProcessBuilder builder = new ProcessBuilder("C:\\Users\\t.eitzenberger\\curl\\bin\\curl.exe", "-v", "http://idp/.well-known/openid-configuration", "--proxy", TigerDirector.getProxySettings());
        System.out.println("TEST Starting curl...");
        Process process = builder.start();
        System.out.println("TEST curl started");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        System.out.println("TEST DONE");
//        Thread.sleep(10000);
    }
}
