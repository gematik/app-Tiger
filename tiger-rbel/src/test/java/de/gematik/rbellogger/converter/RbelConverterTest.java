/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

public class RbelConverterTest {

  @Test
  public void errorDuringConversion_shouldBeIgnored() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    var rbelLogger = RbelLogger.build();
    rbelLogger
        .getRbelConverter()
        .addConverter(
            (el, c) -> {
              if (el.hasFacet(RbelJwtFacet.class)) {
                throw new RuntimeException("this exception should be ignored");
              }
            });

    var convertedMessage =
        rbelLogger
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

    FileUtils.writeStringToFile(
        new File("target/error.html"),
        new RbelHtmlRenderer().doRender(rbelLogger.getMessageHistory()),
        Charset.defaultCharset());

    assertThat(
            convertedMessage
                .findElement("$.body")
                .get()
                .getFacet(RbelNoteFacet.class)
                .get()
                .getValue())
        .contains("this exception should be ignored", this.getClass().getSimpleName());
  }

  @Test
  public void parseMessage_shouldFailBecauseContentIsNull() {
    byte[] content = null;
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              RbelLogger.build()
                  .getRbelConverter()
                  .parseMessage(content, null, null, Optional.of(ZonedDateTime.now()));
            });
  }

  @Test
  public void simulateRaceCondition_PairingShouldBeConserved() {
    RbelElement pair1A = new RbelElement("foo".getBytes(), null);
    RbelElement pair1B = new RbelElement("foo".getBytes(), null);
    RbelElement pair2A = new RbelElement("foo".getBytes(), null);
    RbelElement pair2B = new RbelElement("foo".getBytes(), null);

    pair1A.addFacet(new RbelHttpRequestFacet(null, null, null));
    pair2A.addFacet(new RbelHttpRequestFacet(null, null, null));
    pair1B.addFacet(new RbelHttpResponseFacet(null, null, pair1A));
    pair2B.addFacet(new RbelHttpResponseFacet(null, null, pair2A));

    var rbelLogger = RbelLogger.build();
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair1A, null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair2A, null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair2A, null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair1B, null, null, Optional.of(ZonedDateTime.now()));

    assertThat(pair1B.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()).isEqualTo(pair1A);
    assertThat(pair2B.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()).isEqualTo(pair2A);
  }

  @Test
  public void implicitPairingOfConsecutivePairs() {
    RbelElement pair1A = new RbelElement("foo".getBytes(), null);
    RbelElement pair1B = new RbelElement("foo".getBytes(), null);
    RbelElement pair2A = new RbelElement("foo".getBytes(), null);
    RbelElement pair2B = new RbelElement("foo".getBytes(), null);

    pair1A.addFacet(new RbelHttpRequestFacet(null, null, null));
    pair2A.addFacet(new RbelHttpRequestFacet(null, null, null));
    pair1B.addFacet(new RbelHttpResponseFacet(null, null, null));
    pair2B.addFacet(new RbelHttpResponseFacet(null, null, null));

    var rbelLogger = RbelLogger.build();
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair1A, null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair1B, null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair2A, null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(pair2B, null, null, Optional.of(ZonedDateTime.now()));

    assertThat(pair1B.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()).isEqualTo(pair1A);
    assertThat(pair2B.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()).isEqualTo(pair2A);
  }
}
