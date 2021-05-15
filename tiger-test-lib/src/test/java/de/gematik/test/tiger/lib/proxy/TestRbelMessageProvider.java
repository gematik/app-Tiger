package de.gematik.test.tiger.lib.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelStringElement;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TestRbelMessageProvider {

    @Test public void testTriggerNewReceivedMessageOK() {
        RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage"));
        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).isInstanceOf(RbelStringElement.class);
        assertThat(msgs.get(0).getContent()).isEqualTo("TestMessage");
    }

    @Test public void testTriggerNewReceivedMessageTwoOK() {
        RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage1"));
        rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage2"));
        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).isInstanceOf(RbelStringElement.class);
        assertThat(msgs.get(0).getContent()).isEqualTo("TestMessage1");
        assertThat(msgs.get(1)).isInstanceOf(RbelStringElement.class);
        assertThat(msgs.get(1).getContent()).isEqualTo("TestMessage2");
    }

    @Test public void testWaitForMessageOK() {
        final RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage1"));

        long startms = System.currentTimeMillis();
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Assertions.fail("Failed to wait 2 secs...", e);
            }
            rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage2"));
        }).start();

        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(1);
        rmProvider.waitForMessage();
        long endms = System.currentTimeMillis();
        assertThat(endms - startms).isGreaterThan(1000);
        msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).isInstanceOf(RbelStringElement.class);
        assertThat(msgs.get(0).getContent()).isEqualTo("TestMessage1");
        assertThat(msgs.get(1)).isInstanceOf(RbelStringElement.class);
        assertThat(msgs.get(1).getContent()).isEqualTo("TestMessage2");
    }

    @Test public void testWaitForMessageStartStepOK() {
        final RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage1"));

        long startms = System.currentTimeMillis();
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Assertions.fail("Failed to wait 2 secs...", e);
            }
            rmProvider.startStep(new Step("Given", Collections.emptyList()));
        }).start();

        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(1);
        rmProvider.waitForMessage();
        long endms = System.currentTimeMillis();
        assertThat(endms - startms).isGreaterThan(1000);
        msgs = rmProvider.getMessages();
        assertThat(msgs).isEmpty();
    }

    @Test public void testStartStepOK() {
        final RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage1"));
        rmProvider.triggerNewReceivedMessage(new RbelStringElement("TestMessage2"));

        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).isInstanceOf(RbelStringElement.class);
        assertThat(msgs.get(0).getContent()).isEqualTo("TestMessage1");
        assertThat(msgs.get(1)).isInstanceOf(RbelStringElement.class);
        assertThat(msgs.get(1).getContent()).isEqualTo("TestMessage2");

        rmProvider.startStep(new Step("Given", Collections.emptyList()));
        msgs = rmProvider.getMessages();
        assertThat(msgs).isEmpty();
    }
}
