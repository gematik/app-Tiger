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

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RbelMessageValidatorTest {

    @Test
    public void testPathEqualsWithRelativePath_OK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("/foo/bar?sch=mar"), "/foo/bar"))
            .isTrue();
    }

    @Test
    public void testPathEqualsWithUrl_OK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "/foo/bar"))
            .isTrue();
    }

    @Test
    public void testPathMatchingWithUrlLeading_OK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/.*\\/bar"))
            .isTrue();
    }

    @Test
    public void testPathMatchingWithUrlTrailing_OK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/foo\\/.*"))
            .isTrue();
    }

    @Test
    public void testPathMatchingWithUrlInMid_OK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "\\/foo\\/.*/test"))
            .isTrue();
    }

    @Test
    public void testPathMatchingWithNotMatchRegex_NOK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "/foo/.*/[test]"))
            .isFalse();
    }

    @Test
    public void testPathMatchingWithInvalidRegex_NOK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "["))
            .isFalse();
    }

    @Test
    public void testInvalidPathMatching_NOK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("file$:."), "/foo/.*"))
            .isFalse();
    }

    private RbelElement buildRequestWithPath(final String path) {
        final RbelElement rbelElement = new RbelElement(null, null);
        rbelElement.addFacet(RbelHttpRequestFacet.builder()
            .path(new RbelElement(path.getBytes(), null))
            .build());
        return rbelElement;
    }

    @Test
    public void testHostMatching_OK() {
        assertThat(new RbelMessageValidator().doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "localhost:8080"))
            .isTrue();
    }
    @Test
    public void testHostMatchingRegex_OK() {
        assertThat(new RbelMessageValidator().doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "local.*:8080"))
            .isTrue();
    }

    @Test
    public void testHostMatchingRegexNotMatching_OK() {
        assertThat(new RbelMessageValidator().doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "eitzen.*"))
            .isFalse();
    }
    @Test
    public void testHostMatchingInvalidRegex_NOK() {
        assertThat(new RbelMessageValidator().doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "["))
            .isFalse();
    }

    @Test
    public void testMethodMatching_OK() {
        assertThat(new RbelMessageValidator().doesMethodMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "GET"))
            .isTrue();
    }

    @Test
    public void testMethodMatchingNotMatching_OK() {
        assertThat(new RbelMessageValidator().doesMethodMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "POST"))
            .isFalse();
    }

    private RbelElement buildRequestFromCurlFile(String curlFileName) {
        String curlMessage = readCurlFromFileWithCorrectedLineBreaks(curlFileName, StandardCharsets.UTF_8);
        return RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
    }

    private RbelElement buildResponseFromCurlFile(String curlFileName, RbelElement request) {
        String curlMessage = readCurlFromFileWithCorrectedLineBreaks(curlFileName, StandardCharsets.UTF_8);
        RbelElement message = RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
        message.addOrReplaceFacet(message.getFacet(RbelHttpResponseFacet.class).get().toBuilder().request(request).build());
        return message;
    }

    private String readCurlFromFileWithCorrectedLineBreaks(String fileName, Charset charset) {
        try {
            return FileUtils.readFileToString(new File("src/test/resources/testdata/sampleCurlMessages/" + fileName), charset)
                .replaceAll("(?<!\\r)\\n", "\r\n");
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to read curl file", ioe);
        }
    }

    @Test
    public void testSourceTestInvalid_NOK() {
        assertThatThrownBy(() -> new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body><!-- test comment-- --></body>",
            "<root><header></header><body></body></root>"))
            .isInstanceOf(org.xmlunit.XMLUnitException.class);
    }

    @Test
    public void testSourceOracleInvalid_NOK() {
        assertThatThrownBy(() -> new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body><!-- test comment --></body></root>",
            "<root><header></header><body></body>"))
            .isInstanceOf(org.xmlunit.XMLUnitException.class);
    }

    @Test
    public void testSourceNoComment_OK() {
        new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body><!-- test comment --></body></root>",
            "<root><header></header><body></body></root>", "nocomment");
    }

    @Test
    public void testSourceNoCommetTxtTrim_OK() {
        new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body>test     <!-- test comment --></body></root>",
            "<root><header></header><body>test</body></root>", "nocomment,txttrim");
    }

    @Test
    public void testSourceNoCommetTxtTrim2_OK() {
        new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body>    test     <!-- test comment --></body></root>",
            "<root><header></header><body>test</body></root>", "nocomment,txttrim");
    }

    @Test
    public void testSourceNoCommetTxtTrim3_OK() {
        new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body>    test xxx    <!-- test comment --></body></root>",
            "<root><header></header><body>test xxx</body></root>", "nocomment,txttrim");
    }

    @Test
    public void testSourceNoCommetTxtTrim4_OK() {
        assertThatThrownBy(() -> new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body>    test   xxx    <!-- test comment --></body></root>",
            "<root><header></header><body>test xxx</body></root>", "nocomment,txttrim"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    public void testSourceNoCommetTxtNormalize_OK() {
        new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body>  test    xxxx   </body>  <!-- test comment --></root>",
            "<root><header></header><body>test xxxx </body></root>", "nocomment,txtnormalize");
    }

    @Test
    public void testSourceAttrOrder_OK() {
        new RbelMessageValidator().compareXMLStructure(
            "<root><header></header><body attr1='1'   attr2='2'></body></root>",
            "<root><header></header><body attr2='2' attr1='1'></body></root>");
    }

    @ParameterizedTest
    @CsvSource({"http://server, ''", "http://server/, /", "http://server, /", "http://server/, ''"})
    public void testEmptyPathMatching(final String url, final String path) {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath(url), path))
            .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"http://server/blu/, /", "http://server/, /bla", "http://server/bla, ''"})
    public void testPathOfMessageMatch_NOK(final String url, final String path) {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath(url), path))
            .isFalse();
    }

    @Test
    public void testFilterRequests_OK() {
        addSomeMessagesToTigerTestHooks();
        RbelMessageValidator validator = new RbelMessageValidator();
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());
        RbelElement request = validator.currentRequest;
        assertTrue(validator.doesHostMatch(request, "localhost:8080"));
    }

    @Test
    public void testFilterRequestsWrongPath_OK() {
        addSomeMessagesToTigerTestHooks();
        RbelMessageValidator validator = new RbelMessageValidator();
        assertThatThrownBy(() -> {
            validator.filterRequestsAndStoreInContext(RequestParameter.builder().path("/NOWAY.*").build());
        }).isInstanceOf(AssertionError.class);

    }

    @Test
    public void testFilterRequestsNextRequest_OK() {
        addSomeMessagesToTigerTestHooks();
        RbelMessageValidator validator = new RbelMessageValidator();
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").startFromLastRequest(true).build());
        RbelElement request = validator.currentRequest;
        assertTrue(validator.doesHostMatch(request, "eitzen.at:80"));
    }

    @Test
    public void testFilterRequestsRbelPath_OK() {
        addSomeMessagesToTigerTestHooks();
        RbelMessageValidator validator = new RbelMessageValidator();
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonalagent").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
    }

    @Test
    public void testFilterRequestsRbelPathNotMatching_OK() {
        addSomeMessagesToTigerTestHooks();
        RbelMessageValidator validator = new RbelMessageValidator();
        assertThatThrownBy(() -> {
            validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonalagentXXXX").build());
        }).isInstanceOf(AssertionError.class);
    }
    @Test
    public void testFilterRequestsRbelPathRegex_OK() {
        addSomeMessagesToTigerTestHooks();
        RbelMessageValidator validator = new RbelMessageValidator();
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonal.*").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
    }

    @Test
    public void testFilterRequestsAttachResponseCorrectly_OK() {
        addSomeMessagesToTigerTestHooks();
        RbelMessageValidator validator = new RbelMessageValidator();
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonal.*").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
        assertThat(validator.currentResponse.getFacet(RbelHttpResponseFacet.class).get().getResponseCode().getRawStringContent()).isEqualTo("500");
    }

    private void addSomeMessagesToTigerTestHooks() {
        TigerGlobalConfiguration.putValue("tiger.rbel.request.timeout", 1);
        LocalProxyRbelMessageListener.getValidatableRbelMessages().clear();
        RbelElement request = buildRequestFromCurlFile("getRequestLocalhost.curl");
        LocalProxyRbelMessageListener.getValidatableRbelMessages().add(request);
        LocalProxyRbelMessageListener.getValidatableRbelMessages().add(buildResponseFromCurlFile("htmlMessage.curl", request));
        request = buildRequestFromCurlFile("getRequestEitzenAt.curl");
        LocalProxyRbelMessageListener.getValidatableRbelMessages().add(request);
        LocalProxyRbelMessageListener.getValidatableRbelMessages().add(buildResponseFromCurlFile("htmlMessageEitzenAt.curl", request));
    }
}
