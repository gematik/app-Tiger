/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

public class XmlConverterTest {

    private String curlMessage;
    private String curlMessageHtml;

    @BeforeEach
    public void setUp() throws IOException {
        curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/xmlMessage.curl");
        curlMessageHtml = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/htmlMessage.curl");
    }

    @Test
    public void shouldRenderCleanHtml() throws IOException {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);
        convertedMessage.addFacet(RbelTcpIpMessageFacet.builder()
            .receiver(RbelElement.wrap(null, convertedMessage, new RbelHostname("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, new RbelHostname("sender", 1)))
            .build());

        FileUtils.writeStringToFile(new File("target/xmlNested.html"),
            RbelHtmlRenderer.render(List.of(convertedMessage)));
    }

    @Test
    public void convertMessage_shouldGiveHtmlBody() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessageHtml, null);

        assertThat(convertedMessage.findElement("$.body")
            .get()
            .hasFacet(RbelXmlFacet.class))
            .isTrue();

        assertThat(convertedMessage.findElement("$.body.html.head.link.href"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("jetty-dir.css");
    }

    @Test
    public void convertMessage_shouldGiveXmlBody() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        assertThat(convertedMessage.findRbelPathMembers("$.body").get(0)
            .hasFacet(RbelXmlFacet.class))
            .isTrue();
    }

    @Test
    public void retrieveXmlAttribute_shouldReturnAttributeWithContent() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        assertThat(convertedMessage
            .findRbelPathMembers("$.body.RegistryResponse.status")
            .get(0).getRawStringContent())
            .isEqualTo("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure");
    }

    @Test
    public void retrieveListMemberAttribute() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        final List<RbelElement> deepPathResults = convertedMessage
            .findRbelPathMembers("$.body.RegistryResponse.RegistryErrorList.RegistryError[0].errorCode");
        assertThat(convertedMessage.findRbelPathMembers("$..RegistryError.errorCode"))
            .containsAll(deepPathResults);

        assertThat(deepPathResults.get(0).getRawStringContent())
            .isEqualTo("XDSDuplicateUniqueIdInRegistry");
    }

    @RepeatedTest(10)
    // repeated since this is a test very sensitive to wrong element ordering. It should be our canary in case
    // we screw up the element ordering while parsing!
    public void retrieveTextContent() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        final List<RbelElement> rbelPathResult = convertedMessage.findRbelPathMembers("$..RegistryError[0].text");

        assertThat(rbelPathResult).hasSize(1);
        assertThat(rbelPathResult.get(0).getRawStringContent().trim())
            .isEqualTo("text in element");
    }

    @Test
    public void diveIntoNestedJwt() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        final List<RbelElement> rbelPathResult =
            convertedMessage.findRbelPathMembers("$..jwtTag.text.body.scopes_supported.0");

        assertThat(rbelPathResult).hasSize(1);
        assertThat(rbelPathResult.get(0).getRawStringContent().trim())
            .isEqualTo("openid");
    }

    @Test
    public void retrieveEmptyTextContent() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        final List<RbelElement> rbelPathResult = convertedMessage.findRbelPathMembers("$..textTest.text");

        assertThat(rbelPathResult).hasSize(1);
        assertThat(rbelPathResult.get(0).getRawStringContent())
            .isEqualTo("");
    }

    @Test
    public void retrieveUrlAsTextContent() {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        final List<RbelElement> rbelPathResult = convertedMessage.findRbelPathMembers("$..urlText.text");

        assertThat(rbelPathResult).hasSize(1);
        assertThat(rbelPathResult.get(0).getRawStringContent())
            .isEqualTo("http://url.text.de");
    }

    @Test
    public void longNestedTextContent() throws IOException {
        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(readCurlFromFileWithCorrectedLineBreaks("src/test/resources/XmlWithLongTextNode.curl"),
                null);

        final List<RbelElement> rbelPathResult = convertedMessage
            .findRbelPathMembers(
                "$.body.Envelope.Body.SignDocumentResponse.SignResponse.SignatureObject.Base64Signature.text");

        assertThat(rbelPathResult).hasSize(1);
        assertThat(rbelPathResult.get(0).getRawStringContent()).hasSize(40920);
    }
}
