package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RbelMessageValidatorTest {

    @Test
    public void testPathMatchingWithRelativePath_shouldWork() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("/foo/bar?sch=mar"), "/foo/bar"))
            .isTrue();
    }

    @Test
    public void testPathMatchingWithUrl_shouldWork() {
        assertThat(new RbelMessageValidator().doesPathOfMessageMatch(
            buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "/foo/bar"))
            .isTrue();
    }

    private RbelElement buildRequestWithPath(String path) {
        RbelElement rbelElement = new RbelElement(null, null);
        rbelElement.addFacet(RbelHttpRequestFacet.builder()
            .path(new RbelElement(path.getBytes(), null))
            .build());
        return rbelElement;
    }
}