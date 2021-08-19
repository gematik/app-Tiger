/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.proxy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRbelMessageProvider {

    @Test
    public void testTriggerNewReceivedMessageOK() {
        RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage"));
        assertThat(rmProvider.getMessages().get(0).getRawStringContent()).contains("TestMessage");
    }

    @Test
    public void testTriggerNewReceivedMessageTwoOK() {
        RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));
        assertThat(rmProvider.getMessages()).hasSize(2);
        assertThat(rmProvider.getMessages().get(0).getRawStringContent()).contains("TestMessage1");
        assertThat(rmProvider.getMessages().get(1).getRawStringContent()).contains("TestMessage2");
    }

    @Test
    public void testWaitForMessageOK() {
        final RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));

        long startms = System.currentTimeMillis();
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));
        }).start();

        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(1);
        rmProvider.waitForMessage();
        long endms = System.currentTimeMillis();
        assertThat(endms - startms).isGreaterThan(1000);
        assertThat(rmProvider.getMessages()).hasSize(2);
        assertThat(rmProvider.getMessages().get(0).getRawStringContent()).contains("TestMessage1");
        assertThat(rmProvider.getMessages().get(1).getRawStringContent()).contains("TestMessage2");
    }

    @Test
    public void testWaitForMessageStartStepOK() {
        final RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));

        long startms = System.currentTimeMillis();
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            rmProvider.startStep();
        }).start();

        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(1);
        rmProvider.waitForMessage();
        long endms = System.currentTimeMillis();
        assertThat(endms - startms).isGreaterThan(1000);
        msgs = rmProvider.getMessages();
        assertThat(msgs).isEmpty();
    }

    @Test
    public void testStartStepOK() {
        final RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));

        List<RbelElement> msgs = rmProvider.getMessages();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).getRawStringContent()).contains("TestMessage1");
        assertThat(msgs.get(1).getRawStringContent()).contains("TestMessage2");

        rmProvider.startStep();
        msgs = rmProvider.getMessages();
        assertThat(msgs).isEmpty();
    }

    private RbelElement buildMessageWithContent(String messageBody) {
        return RbelElement.builder()
                .rawContent(messageBody.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}
