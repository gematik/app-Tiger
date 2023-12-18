/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter.listener;

import static de.gematik.rbellogger.TestUtils.localhostWithPort;
import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelBundleCriterion;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelBundledMessagesPluginTest {

  private RbelLogger rbelLogger;

  @BeforeEach
  public void initRbelLogger() {
    if (rbelLogger == null) {
      rbelLogger = RbelLogger.build();
    }
    rbelLogger.getRbelModifier().deleteAllModifications();
  }

  @Test
  void testBundledMessagesWithOnlyRequests() throws IOException {
    rbelLogger.addBundleCriterion(
        RbelBundleCriterion.builder()
            .bundledServerName("kombi")
            .receiver(List.of("$.receiver.port == 5432"))
            .sender(List.of("$.sender.port == 54321"))
            .build());

    RbelElement requestLeft =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(44444),
            localhostWithPort(5432));
    RbelElement requestRight =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(54321),
            localhostWithPort(6543));

    assertThat(requestLeft.findElement("$.receiver").get().getFacetOrFail(RbelHostnameFacet.class))
        .hasToString("kombi:5432");
    assertThat(requestRight.findElement("$.sender").get().getFacetOrFail(RbelHostnameFacet.class))
        .hasToString("kombi:54321");
  }

  @Test
  void testBundledMessagesWithOnlyResponses() throws IOException {
    rbelLogger.addBundleCriterion(
        RbelBundleCriterion.builder()
            .bundledServerName("kombi")
            .receiver(List.of("$.receiver.port == 5432")) // implicit "$.receiver.port == 54321"
            .sender(List.of("$.sender.port == 54321")) // implicit "$.sender.port == 5432"
            .build());

    RbelElement requestRight =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(54321),
            localhostWithPort(6543));
    RbelElement responseRight =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/xmlMessage.curl",
            localhostWithPort(6543),
            localhostWithPort(54321));

    RbelElement requestLeft =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(44444),
            localhostWithPort(5432));
    RbelElement responseLeft =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/xmlMessage.curl",
            localhostWithPort(5432),
            localhostWithPort(44444));

    assertThat(requestLeft.findElement("$.receiver").get().getFacetOrFail(RbelHostnameFacet.class))
        .hasToString("kombi:5432");
    assertThat(responseLeft.findElement("$.sender").get().getFacetOrFail(RbelHostnameFacet.class))
        .hasToString("kombi:5432");

    assertThat(requestRight.findElement("$.sender").get().getFacetOrFail(RbelHostnameFacet.class))
        .hasToString("kombi:54321");
    assertThat(
            responseRight.findElement("$.receiver").get().getFacetOrFail(RbelHostnameFacet.class))
        .hasToString("kombi:54321");
  }

  @Test
  void shouldAddNothingBecausePortIsWrong() throws IOException {
    rbelLogger.addBundleCriterion(
        RbelBundleCriterion.builder()
            .bundledServerName("kombi")
            .receiver(List.of("$.receiver.port == 5432"))
            .sender(List.of("$.sender.port == 54321"))
            .build());

    RbelElement requestLeft =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(44444),
            localhostWithPort(543211111));

    assertThat(
            requestLeft
                .getFacet(RbelTcpIpMessageFacet.class)
                .get()
                .getReceiver()
                .getFacet(RbelHostnameFacet.class)
                .get()
                .getBundledServerName())
        .isEmpty();
  }

  @Test
  void shouldAddNothingBecauseListsAreEmpty() throws IOException {
    rbelLogger.addBundleCriterion(
        RbelBundleCriterion.builder()
            .bundledServerName("kombi")
            .receiver(null)
            .sender(null)
            .build());

    RbelElement requestLeft =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(44444),
            localhostWithPort(543211111));
    RbelElement requestRight =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(54321),
            localhostWithPort(6543));

    assertThat(
            requestLeft
                .getFacet(RbelTcpIpMessageFacet.class)
                .get()
                .getReceiver()
                .getFacet(RbelHostnameFacet.class)
                .get()
                .getBundledServerName())
        .isEmpty();
    assertThat(
            requestLeft
                .getFacet(RbelTcpIpMessageFacet.class)
                .get()
                .getSender()
                .getFacet(RbelHostnameFacet.class)
                .get()
                .getBundledServerName())
        .isEmpty();
  }

  @Test
  void verifyCorrectSenderAndReceiverHostnames() throws IOException {
    rbelLogger.addBundleCriterion(
        RbelBundleCriterion.builder()
            .bundledServerName("kombi")
            .receiver(List.of("$.receiver.port == 5432"))
            .sender(List.of("$.sender.port == 54321"))
            .build());

    RbelElement requestLeft =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(44444),
            localhostWithPort(5432));
    RbelElement requestRight =
        modifyMessageAndParseResponse(
            "src/test/resources/sampleMessages/getRequest.curl",
            localhostWithPort(54321),
            localhostWithPort(6543));

    assertThat(requestLeft.getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiverHostname())
        .hasToString("kombi:5432");
    assertThat(requestLeft.getFacetOrFail(RbelTcpIpMessageFacet.class).getSenderHostname())
        .hasToString("localhost:44444");
  }

  private RbelElement modifyMessageAndParseResponse(
      String filename, RbelHostname sender, RbelHostname receiver) throws IOException {
    return rbelLogger
        .getRbelConverter()
        .parseMessage(
            readCurlFromFileWithCorrectedLineBreaks(filename).getBytes(),
            sender,
            receiver,
            Optional.of(ZonedDateTime.now()));
  }
}
