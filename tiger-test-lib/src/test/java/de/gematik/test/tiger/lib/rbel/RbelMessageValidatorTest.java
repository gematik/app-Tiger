/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import org.junit.Test;

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
    public void testPathMatchingWithInvalidRegex_NOK() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "/foo/.*/[test]"))
            .isFalse();
    }

    private RbelElement buildRequestWithPath(String path) {
        RbelElement rbelElement = new RbelElement(null, null);
        rbelElement.addFacet(RbelHttpRequestFacet.builder()
            .path(new RbelElement(path.getBytes(), null))
            .build());
        return rbelElement;
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
}
