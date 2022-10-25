/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.addTwoRequestsToTigerTestHooks;
import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.buildRequestFromCurlFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelMessageValidatorTest {

    @Test
    void testPathEqualsWithRelativePath_OK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath("/foo/bar?sch=mar"), "/foo/bar"))
            .isTrue();
    }

    @Test
    void testPathEqualsWithUrl_OK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "/foo/bar"))
            .isTrue();
    }

    @Test
    void testPathMatchingWithUrlLeading_OK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/.*\\/bar"))
            .isTrue();
    }

    @Test
    void testPathMatchingWithUrlTrailing_OK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/foo\\/.*"))
            .isTrue();
    }

    @Test
    void testPathMatchingWithUrlInMid_OK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "\\/foo\\/.*/test"))
            .isTrue();
    }

    @Test
    void testPathMatchingWithNotMatchRegex_NOK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "/foo/.*/[test]"))
            .isFalse();
    }

    @Test
    void testPathMatchingWithInvalidRegex_NOK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "["))
            .isFalse();
    }

    @Test
    void testInvalidPathMatching_NOK() {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
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
    void testHostMatching_OK() {
        assertThat(RbelMessageValidator.instance.doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "localhost:8080"))
            .isTrue();
    }

    @Test
    void testHostMatchingRegex_OK() {
        assertThat(RbelMessageValidator.instance.doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "local.*:8080"))
            .isTrue();
    }

    @Test
    void testHostMatchingRegexNotMatching_OK() {
        assertThat(RbelMessageValidator.instance.doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "eitzen.*"))
            .isFalse();
    }

    @Test
    void testHostMatchingInvalidRegex_NOK() {
        assertThat(RbelMessageValidator.instance.doesHostMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "["))
            .isFalse();
    }

    @Test
    void testMethodMatching_OK() {
        assertThat(RbelMessageValidator.instance.doesMethodMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "GET"))
            .isTrue();
    }

    @Test
    void testMethodMatchingNotMatching_OK() {
        assertThat(RbelMessageValidator.instance.doesMethodMatch(
            buildRequestFromCurlFile("getRequestLocalhost.curl"), "POST"))
            .isFalse();
    }

    @Test
    void testSourceTestInvalid_NOK() {
        assertThatThrownBy(() -> RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body><!-- test comment-- --></body>",
            "<root><header></header><body></body></root>"))
            .isInstanceOf(org.xmlunit.XMLUnitException.class);
    }

    @Test
    void testSourceOracleInvalid_NOK() {
        assertThatThrownBy(() -> RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body><!-- test comment --></body></root>",
            "<root><header></header><body></body>"))
            .isInstanceOf(org.xmlunit.XMLUnitException.class);
    }

    @Test
    void testSourceNoComment_OK() {
        RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body><!-- test comment --></body></root>",
            "<root><header></header><body></body></root>", "nocomment");
        assertThatNoException();
    }

    @Test
    void testSourceNoCommetTxtTrim_OK() {
        RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body>test     <!-- test comment --></body></root>",
            "<root><header></header><body>test</body></root>", "nocomment,txttrim");
        assertThatNoException();
    }

    @Test
    void testSourceNoCommetTxtTrim2_OK() {
        RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body>    test     <!-- test comment --></body></root>",
            "<root><header></header><body>test</body></root>", "nocomment,txttrim");
        assertThatNoException();
    }

    @Test
    void testSourceNoCommetTxtTrim3_OK() {
        RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body>    test xxx    <!-- test comment --></body></root>",
            "<root><header></header><body>test xxx</body></root>", "nocomment,txttrim");
        assertThatNoException();
    }

    @Test
    void testSourceNoCommetTxtTrim4_OK() {
        assertThatThrownBy(() -> RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body>    test   xxx    <!-- test comment --></body></root>",
            "<root><header></header><body>test xxx</body></root>", "nocomment,txttrim"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void testSourceNoCommetTxtNormalize_OK() {
        RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body>  test    xxxx   </body>  <!-- test comment --></root>",
            "<root><header></header><body>test xxxx </body></root>", "nocomment,txtnormalize");
        assertThatNoException();
    }

    @Test
    void testSourceAttrOrder_OK() {
        RbelMessageValidator.instance.compareXMLStructure(
            "<root><header></header><body attr1='1'   attr2='2'></body></root>",
            "<root><header></header><body attr2='2' attr1='1'></body></root>");
        assertThatNoException();
    }

    @ParameterizedTest
    @CsvSource({"http://server, ''", "http://server/, /", "http://server, /", "http://server/, ''"})
    void testEmptyPathMatching(final String url, final String path) {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath(url), path))
            .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"http://server/blu/, /", "http://server/, /bla", "http://server/bla, ''"})
    void testPathOfMessageMatch_NOK(final String url, final String path) {
        assertThat(RbelMessageValidator.instance.doesPathOfMessageMatch(
            buildRequestWithPath(url), path))
            .isFalse();
    }

    @Test
    void testFilterRequests_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());
        RbelElement request = validator.currentRequest;
        assertTrue(validator.doesHostMatch(request, "localhost:8080"));
    }

    @Test
    void testFilterRequestsWrongPath_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        final RequestParameter requestParameter = RequestParameter.builder().path("/NOWAY.*").build();
        assertThatThrownBy(() -> validator.filterRequestsAndStoreInContext(requestParameter))
            .isInstanceOf(AssertionError.class);

    }

    @Test
    void testFilterRequestsNextRequest_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());
        validator.filterRequestsAndStoreInContext(
            RequestParameter.builder().path(".*").startFromLastRequest(true).build());
        RbelElement request = validator.currentRequest;
        assertTrue(validator.doesHostMatch(request, "eitzen.at:80"));
    }

    @Test
    void testFilterRequestsRbelPath_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        validator.filterRequestsAndStoreInContext(
            RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonalagent").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
    }

    @Test
    void testFilterRequestsRbelPathNotMatching_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        assertThatThrownBy(() -> {
            validator.filterRequestsAndStoreInContext(
                RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonalagentXXXX")
                    .build());
        }).isInstanceOf(AssertionError.class);
    }

    @Test
    void testFilterRequestsRbelPathRegex_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        validator.filterRequestsAndStoreInContext(
            RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonal.*").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
    }

    @Test
    void testFilterRequestsRbelPathExists_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        validator.filterRequestsAndStoreInContext(
            RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "localhost:8080"));
    }

    @Test
    void testFilterRequestsRbelPathExists2_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        validator.filterRequestsAndStoreInContext(
            RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
    }

    @Test
    void testFilterRequestsRbelPathExists_NOK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        assertThatThrownBy(() -> {
            validator.filterRequestsAndStoreInContext(
                RequestParameter.builder().path(".*").rbelPath("$.header.User-AgentXXX").build());
        }).isInstanceOf(AssertionError.class);
    }


    @Test
    void testFilterRequestsAttachResponseCorrectly_OK() {
        addTwoRequestsToTigerTestHooks();
        RbelMessageValidator validator = RbelMessageValidator.instance;
        validator.filterRequestsAndStoreInContext(
            RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").value("mypersonal.*").build());
        assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
        assertThat(validator.currentResponse.getFacet(RbelHttpResponseFacet.class).get().getResponseCode()
            .getRawStringContent()).isEqualTo("500");
    }
}
