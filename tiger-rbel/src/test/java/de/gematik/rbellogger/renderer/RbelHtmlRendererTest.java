/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.renderer;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.rbellogger.util.RbelValueShader;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelHtmlRendererTest {

  private static final RbelConverter RBEL_CONVERTER =
      RbelLogger.build(
              new RbelConfiguration().activateConversionFor("ASN1").activateConversionFor("X509"))
          .getRbelConverter();
  private static final RbelHtmlRenderer RENDERER = new RbelHtmlRenderer();

  @BeforeEach
  void resetConfig() {
    TigerGlobalConfiguration.reset();
  }

  @Test
  void convertToHtml() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelElement convertedMessage = RBEL_CONVERTER.convertElement(curlMessage, null);

    final String render =
        RbelHtmlRenderer.render(wrapHttpMessage(convertedMessage, ZonedDateTime.now()));
    FileUtils.writeStringToFile(new File("target/out.html"), render, Charset.defaultCharset());
    assertThat(Jsoup.parse(render)).isNotNull();
  }

  @Test
  void valueShading() throws IOException {
    RENDERER.setRenderNestedObjectsWithoutFacetRenderer(true);
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelElement convertedMessage = RBEL_CONVERTER.convertElement(curlMessage, null);
    convertedMessage.addFacet(new RbelNoteFacet("foobar Message"));
    convertedMessage.getFirst("header").get().addFacet(new RbelNoteFacet("foobar Header"));
    convertedMessage
        .getFirst("header")
        .get()
        .getChildNodesStream()
        .forEach(
            element -> {
              for (int i = 0; i < RandomUtils.nextInt(0, 4); i++) {
                element.addFacet(
                    new RbelNoteFacet(
                        "some note " + RandomStringUtils.insecure().nextAlphanumeric(30),
                        RbelNoteFacet.NoteStyling.values()[RandomUtils.nextInt(0, 3)]));
              }
            });
    convertedMessage.getFirst("body").get().addFacet(new RbelNoteFacet("foobar Body"));
    convertedMessage
        .findElement("$.body.header")
        .get()
        .addFacet(new RbelNoteFacet("foobar JWT Header"));
    convertedMessage
        .findElement("$.body.body")
        .get()
        .addFacet(new RbelNoteFacet("foobar JWT Body"));
    convertedMessage
        .findElement("$.body.signature")
        .get()
        .addFacet(new RbelNoteFacet("foobar Signature"));
    convertedMessage
        .findElement("$.body.body.jwks_uri")
        .get()
        .addFacet(new RbelNoteFacet("jwks_uri: note im JSON"));
    convertedMessage
        .findElement("$.body.body.jwks_uri")
        .get()
        .addFacet(new RbelNoteFacet("warnung", RbelNoteFacet.NoteStyling.WARN));
    convertedMessage
        .findElement("$.body.body.scopes_supported")
        .get()
        .addFacet(new RbelNoteFacet("scopes_supported: note an einem array"));

    final String convertedHtml =
        RENDERER.render(
            wrapHttpMessage(convertedMessage, ZonedDateTime.now()),
            new RbelValueShader()
                .addSimpleShadingCriterion("Date", "<halt ein date>")
                .addSimpleShadingCriterion("Content-Length", "<Die Länge. Hier %s>")
                .addSimpleShadingCriterion("exp", "<Nested Shading>")
                .addSimpleShadingCriterion("nbf", "\"foobar\"")
                .addSimpleShadingCriterion("iat", "&some&more\"stuff\""));
    FileUtils.writeStringToFile(new File("target/shaded.html"), convertedHtml);

    assertThat(convertedHtml)
        .contains("&lt;halt ein date&gt;")
        .contains("&lt;Die Länge. Hier 2653&gt;")
        .contains("\"&quot;foobar&quot;\"")
        .contains("&amp;some&amp;more&quot;stuff&quot;");
  }

  @Test
  void advancedShading() throws IOException {
    RENDERER.setRenderNestedObjectsWithoutFacetRenderer(true);
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final String convertedHtml =
        RENDERER.render(
            wrapHttpMessage(RBEL_CONVERTER.convertElement(curlMessage, null), ZonedDateTime.now()),
            new RbelValueShader()
                .addJexlShadingCriterion("key == 'Version'", "<version: %s>")
                .addJexlShadingCriterion(
                    "key == 'nbf' && empty(element.parentNode)", "<nbf in JWT: %s>"));

    FileUtils.writeStringToFile(new File("target/out.html"), convertedHtml);

    assertThat(convertedHtml)
        .contains("&lt;version: 9.0.0&gt;")
        .contains("nbf-Wert in http header")
        .contains("&lt;nbf in JWT: 1614339303&gt;")
        .doesNotContain("nbf in JWT: nbf-Wert in http header");
  }

  @Test
  void onlyServerNameKnown_shouldStillRender() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelElement convertedMessage =
        RBEL_CONVERTER.parseMessage(
            curlMessage.getBytes(),
            new RbelMessageMetadata()
                .withSender(RbelSocketAddress.create("foobar", 666))
                .withTransmissionTime(ZonedDateTime.now()));

    final String convertedHtml = RENDERER.render(List.of(convertedMessage));

    FileUtils.writeStringToFile(new File("target/out.html"), convertedHtml);

    assertThat(convertedHtml)
        .contains("foobar:666") // hostname
        .contains("487275465566779"); // Serialnumber of cert
  }

  @Test
  void shouldContainTimeStamps() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelElement convertedMessage = RBEL_CONVERTER.convertElement(curlMessage, null);

    final ZonedDateTime transmissionTime = ZonedDateTime.now();

    assertThat(RbelHtmlRenderer.render(wrapHttpMessage(convertedMessage, transmissionTime)))
        .contains(transmissionTime.format(DateTimeFormatter.ISO_TIME));
  }

  @Test
  void shouldRenderBinaryMessagesDirectly() throws IOException {
    final byte[] content = Base64.getDecoder().decode("awAAAUEAAAAADoAoAAAIaQYTABMAEwD/");
    final RbelElement convertedMessage =
        RBEL_CONVERTER.parseMessage(
            content,
            new RbelMessageMetadata()
                .withSender(RbelSocketAddress.create("sender", 1))
                .withReceiver(RbelSocketAddress.create("receiver", 1))
                .withTransmissionTime(ZonedDateTime.now()));
    convertedMessage.addFacet(new RbelBinaryFacet());

    final String convertedHtml = RENDERER.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(new File("target/binary.html"), convertedHtml);

    assertThat(convertedHtml)
        .contains("08 69")
        .contains("06 13 00 13 00 13 00 ff")
        .contains(".i")
        .contains("........");
  }

  @Test
  void shouldRenderXmlMessagesDirectly() throws IOException {
    byte[] xmlBytes = FileUtils.readFileToByteArray(new File("src/test/resources/randomXml.xml"));
    final RbelElement convertedMessage =
        RBEL_CONVERTER.parseMessage(
            xmlBytes,
            new RbelMessageMetadata()
                .withSender(RbelSocketAddress.create("sender", 13421))
                .withReceiver(RbelSocketAddress.create("receiver", 14512))
                .withTransmissionTime(ZonedDateTime.now()));

    final String convertedHtml = RENDERER.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(new File("target/directXml.html"), convertedHtml);

    assertThat(convertedHtml).contains("Configuration status=").contains("sender:13421");
  }

  @Test
  void shouldRenderHtmlMessagesWithoutError() throws IOException {
    byte[] htmlBytes = FileUtils.readFileToByteArray(new File("src/test/resources/sample.html"));
    final RbelElement convertedMessage =
        RBEL_CONVERTER.parseMessage(
            htmlBytes,
            new RbelMessageMetadata()
                .withSender(RbelSocketAddress.create("sender", 13421))
                .withReceiver(RbelSocketAddress.create("receiver", 14512))
                .withTransmissionTime(ZonedDateTime.now()));

    final String convertedHtml = RENDERER.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(new File("target/directHtml.html"), convertedHtml);

    assertThat(convertedHtml).contains("\n       &lt;li&gt;LoginCreateToken");
  }

  @Test
  void logoFilePathSet_ShouldBeUsed() throws IOException {
    TigerGlobalConfiguration.putValue("tiger.lib.rbelLogoFilePath", "pom.xml");
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelElement convertedMessage = RBEL_CONVERTER.convertElement(curlMessage, null);

    final String render =
        RbelHtmlRenderer.render(wrapHttpMessage(convertedMessage, ZonedDateTime.now()));
    assertThat(render)
        .contains(
            "data:image/png;base64,"
                +
                // pom always starts with '<?xml'. Strip the rest to avoid trailing blank bytes.
                Base64.getEncoder()
                    .encodeToString("<?xml".getBytes(StandardCharsets.UTF_8))
                    .substring(0, 6));
  }

  @Test
  void logoFilePathNotSet_ShouldDisplayStandardLogo() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelElement convertedMessage = RBEL_CONVERTER.convertElement(curlMessage, null);

    final String render =
        RbelHtmlRenderer.render(wrapHttpMessage(convertedMessage, ZonedDateTime.now()));
    assertThat(render).contains("/png;base64,iVBORw0K");
  }

  private List<RbelElement> wrapHttpMessage(
      RbelElement convertedMessage, ZonedDateTime... transmissionTime) {
    convertedMessage.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(
                RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("sender", 1)))
            .build());
    convertedMessage.addFacet(
        RbelMessageTimingFacet.builder()
            .transmissionTime(transmissionTime == null ? ZonedDateTime.now() : transmissionTime[0])
            .build());
    return List.of(convertedMessage);
  }
}
