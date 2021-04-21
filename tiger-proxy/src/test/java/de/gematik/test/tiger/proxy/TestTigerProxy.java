package de.gematik.test.tiger.proxy;

import org.junit.Test;

public class TestTigerProxy {

    @Test
    public void testProxy() throws InterruptedException {
        final TigerProxy tp = new TigerProxy();
        while (true) {
            Thread.sleep(1000);
        }
    }
}
