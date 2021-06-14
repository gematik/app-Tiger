/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.proxy;

import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.rbellogger.data.elements.RbelHttpMessage;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.proxy.IRbelMessageListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RbelMessageProvider implements IRbelMessageListener {

    // TODO how to make it safe for multithreaded test scenarios
    // use multiple proxies, one for each THREAD!
    private final List<RbelMessage> messages = new ArrayList<>();

    private long timeoutms = 5000;

    private boolean wait = false;

    @Override
    public void triggerNewReceivedMessage(RbelMessage el) {
        messages.add(el);
        wait = false;
    }

    public void waitForMessage() {
        wait = true;
        long startms = System.currentTimeMillis();
        while (wait) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (System.currentTimeMillis() - startms > timeoutms) {
                throw new TigerLibraryException("Timeout waiting for rbel message");
            }
        }
    }

    public RbelMessage pullMessage() {
        if (messages.isEmpty()) {
            waitForMessage();
        }
        RbelMessage el = messages.get(0);
        messages.remove(0);
        return el;
    }

    public List<RbelHttpMessage> getHttpMessages() {
        return messages.stream()
            .map(RbelMessage::getHttpMessage)
            .collect(Collectors.toList());
    }

    public List<RbelMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }


    public void startStep(Step st) {
        messages.clear(); wait = false;
    }

    public void setTimeoutms(long timeout) {
        timeoutms = timeout;
    }
}
