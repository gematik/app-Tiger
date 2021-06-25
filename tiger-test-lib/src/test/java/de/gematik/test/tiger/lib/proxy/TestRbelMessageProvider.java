/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.rbellogger.data.elements.*;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TestRbelMessageProvider {

    @Test
    public void testTriggerNewReceivedMessageOK() {
        RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage"));
        assertThat(rmProvider.getHttpMessages().get(0).getContent()).contains("TestMessage");
    }

    @Test
    public void testTriggerNewReceivedMessageTwoOK() {
        RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));
        assertThat(rmProvider.getHttpMessages()).hasSize(2);
        assertThat(rmProvider.getHttpMessages().get(0).getContent()).contains("TestMessage1");
        assertThat(rmProvider.getHttpMessages().get(1).getContent()).contains("TestMessage2");
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

        List<RbelHttpMessage> msgs = rmProvider.getHttpMessages();
        assertThat(msgs).hasSize(1);
        rmProvider.waitForMessage();
        long endms = System.currentTimeMillis();
        assertThat(endms - startms).isGreaterThan(1000);
        msgs = rmProvider.getHttpMessages();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).getContent()).contains("TestMessage1");
        assertThat(msgs.get(1).getContent()).contains("TestMessage2");
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
            rmProvider.startStep(new Step("Given", Collections.emptyList()));
        }).start();

        List<RbelHttpMessage> msgs = rmProvider.getHttpMessages();
        assertThat(msgs).hasSize(1);
        rmProvider.waitForMessage();
        long endms = System.currentTimeMillis();
        assertThat(endms - startms).isGreaterThan(1000);
        msgs = rmProvider.getHttpMessages();
        assertThat(msgs).isEmpty();
    }

    @Test
    public void testStartStepOK() {
        final RbelMessageProvider rmProvider = new RbelMessageProvider();
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
        rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));

        List<RbelHttpMessage> msgs = rmProvider.getHttpMessages();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0).getContent()).contains("TestMessage1");
        assertThat(msgs.get(1).getContent()).contains("TestMessage2");

        rmProvider.startStep(new Step("Given", Collections.emptyList()));
        msgs = rmProvider.getHttpMessages();
        assertThat(msgs).isEmpty();
    }

    private RbelMessage buildMessageWithContent(String messageBody) {
        return RbelMessage.builder()
            .httpMessage(RbelHttpResponse.builder()
                .body(new RbelStringElement(messageBody))
                .header(new RbelMultiValuedMapElement())
                .responseCode(200)
                .build())
            .build();
    }
}
