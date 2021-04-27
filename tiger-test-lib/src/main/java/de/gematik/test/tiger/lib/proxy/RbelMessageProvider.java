package de.gematik.test.tiger.lib.proxy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.proxy.IRbelMessageListener;
import java.util.ArrayList;
import java.util.List;

public class RbelMessageProvider implements IRbelMessageListener {

    // TODO how to make it safe for multithreaded test scenarios
    private final List<RbelElement> messages = new ArrayList<>();

    private boolean wait = false;

    @Override
    public void rbelMessageReceived(RbelElement el) {
        messages.add(el);
        wait = false;
    }

    public void waitForMessage() {
        wait = true;
        while (wait) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startStep(Step st) {
        messages.clear();
    }
}
