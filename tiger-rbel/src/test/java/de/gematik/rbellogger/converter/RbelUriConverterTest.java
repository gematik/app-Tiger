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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJweFacet;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import lombok.val;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.client.utils.URIUtils;
import org.eclipse.jetty.util.URIUtil;
import org.junit.jupiter.api.Test;

public class RbelUriConverterTest {

    @Test
    public void convertAbsolutePathWithQueryParameters() {
        final RbelElement rbelElement = RbelLogger.build().getRbelConverter().convertElement("/foobar?"
            + "&nonce=997755"
            + "&client_id=fdsafds"
            + "&code_challenge=Ca3Ve8jSsBQOBFVqQvLs1E-dGV1BXg2FTvrd-Tg19Vg", null);

        assertThat(rbelElement.getFacet(RbelUriFacet.class))
            .isPresent();
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getBasicPathString())
            .isEqualTo("/foobar");
        assertThat(rbelElement
            .getFacetOrFail(RbelUriFacet.class).getQueryParameters().stream()
            .map(el -> el.getFacetOrFail(RbelUriParameterFacet.class))
            .map(RbelUriParameterFacet::getKeyAsString)
            .collect(Collectors.toList()))
            .containsExactly("nonce", "client_id", "code_challenge");
    }

    @Test
    public void domainAndProtocolAndQuery_shouldConvertNested() {
        final RbelElement rbelElement = RbelLogger.build().getRbelConverter().convertElement(
            "http://redirect.gematik.de/erezept/token?code=eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIiwiZXhwIjoxNjE0NDE2MTUxfQ..yo1fe7ZrDMhZIz7Z.cWGs95g2mOeVGkqzQXQo9EsTMAOtJbEBfGzqPWIGrWwOoQ_EibAlC7ypku6tuCTYQcGvG4BTdlBT_j1OsKrgDgZXaW2SCw-AwunD4pWkKKXK1xMmF1HSdLEf_Vr6yENq6eamxAdNBE8J6guDy4MCogeBe8TtmXr451Jvg3VRMUMQShWYGkjr5iVj0hfmQXsQGnFUnBPH460Ai-m9K8vQdDHa-4eUa407I8DQJLQRCbvgPFIBzTBYvLZYl9942qlsC_TFoObuzic_1UO0lGRa01JRjAgOVOOYFO_ZCr3bSCX2VRa9mnRhnECLITwTZnt1vCW3umXOANNNNYI-4o0NIHUet3Tz2Qget8ZjXRjM8XljesrQyYOBDu6zX-katT3U8LRtf1jS5oz7mpcbCjSSkiM25QteqLdY2h9mmoEPcV0ZTs8HN4OEQASPBYkyXFHtEPdTzMvONqIMNn3tG3xQAk_u1wGfapoYS31_P8jmZoePCmJR0xBTQxHpWXF_-kxBz2RPran8RahBmJP-sucuTGVxLrQ2pLH8cj0l20CeS10ax8D0aXUVy-FEh_qLHc65VjCzHjNZNdIDJug6mmd8vHn3d_Y0g-LNHqTA2WfE3vIvsRX8YAStwjNhyi-Iz90GTlkoEpaCg8xEqhANvkUgz1hDGVuEU9hCEBHJsll-hoCFnTSHrTzZkbQK3ccMNSCeANctepESAzlc_8MhtpJ6tiDBoHq7o2R4Lm6rI6Vb5CEpP8ExuFFX6jxA2_U_Sr49N0bdiF4LUG-kbk6A9GkkHobjhBB6UJqgekuYWb3zC-x66MIVPE-VQ0yvBCeFw0qerHNtIi0MmWkxPT2I9xm3dHf01WM639DC4mNjVG2ZpIxMFkyGWmg1GeRnyZHFCAM7KSMfVZlJ7Ixlc4kXQuvoHu-X04SJvbw4SYxdKzSjUGoHsEPLnN1fKGweWMtiE84vb1Lmypj7g5uzp2otlaJUGv2nYwMEDzlT3dNNLQeFIyyNmwccPGM1RKOkdV3xuktq-2qPEJGhuxMAFdgFT0sptXSxAqpu5ExAZFBABQLA_mqyQkgC7PFhGewe_tvjVgVDT0Py_ne8pVAnscEatllut0MzvR2ZuiOZb6al44nYgYGNzqnUW3ZEOrfH52hp3mtYDNss8jxGI5kO9MR0SrnEyFzeC1MtoHm4FAkW9R44KJwMNPdDsDsW9jrGYiluaTDxZuyq9VVLmbP-zbID2-kA65A2k-1tfLu0MO8Piv14neccyHWO82j40QjDzK53vAa81vLFHM03N68XtV0WYq3dUZVhMcUGVAfGxEiV-cHbfUFZys_EguthrxdhUpYCULzDnNvEzx16haI6JA.1t26fHLOfI3-kGOYW1fEQg&ssotoken=eyJ2ow&state=xxxstatexxx1a",
            null);

        assertThat(rbelElement.getFacet(RbelUriFacet.class))
            .isPresent();
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getBasicPathString())
            .isEqualTo("http://redirect.gematik.de/erezept/token");
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getQueryParameters().stream()
            .map(el -> el.getFacetOrFail(RbelUriParameterFacet.class))
            .map(RbelUriParameterFacet::getKeyAsString)
            .collect(Collectors.toList()))
            .containsOnly("code", "ssotoken", "state");
        final RbelElement code = rbelElement.getFacetOrFail(RbelUriFacet.class).getQueryParameter("code")
            .get();
        assertThat(code.hasFacet(RbelJweFacet.class))
            .isTrue();
        assertThat(rbelElement.traverseAndReturnNestedMembers())
            .contains(code);
    }

    @Test
    public void urlEscapedParameterValues_shouldContainOriginalContent() {
        final RbelElement rbelElement = RbelLogger.build().getRbelConverter().convertElement(
            "/endpoint?scope=pairing%20openid&foo=bar+schmar",
            null);

        assertThat(rbelElement.findElement("$.scope").get().getRawStringContent())
            .isEqualTo("scope=pairing%20openid");
        assertThat(rbelElement.findElement("$.scope.value").get().getRawStringContent())
            .isEqualTo("pairing openid");
        assertThat(rbelElement.findElement("$.foo").get().getRawStringContent())
            .isEqualTo("foo=bar+schmar");
        assertThat(rbelElement.findElement("$.foo.value").get().getRawStringContent())
            .isEqualTo("bar schmar");
    }

    @Test
    public void simpleUrlWithoutQuery() {
        final RbelElement rbelElement = RbelLogger.build().getRbelConverter()
            .convertElement("http://redirect.gematik.de/foo/bar", null);

        assertThat(rbelElement.hasFacet(RbelUriFacet.class))
            .isTrue();
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getBasicPath().getRawStringContent())
            .isEqualTo("http://redirect.gematik.de/foo/bar");
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getQueryParameters())
            .isEmpty();
    }

    @Test
    public void longSpecialCaseParameter() throws UnsupportedEncodingException {
        final String sourceParameter = RandomStringUtils.randomPrint(3000);
        String specialCaseParameter = URLEncoder.encode(sourceParameter,
            StandardCharsets.UTF_8.name());

        final String basePath = "http://redirect.gematik.de/foo/bar";
        final String url = basePath + "?foo=" + specialCaseParameter + "&blub=blab";
        final RbelElement rbelElement = RbelLogger.build().getRbelConverter()
            .convertElement(url, null);

        assertThat(rbelElement.hasFacet(RbelUriFacet.class))
            .isTrue();
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getBasicPath().getRawStringContent())
            .isEqualTo(basePath);
        assertThat(rbelElement.findElement("$.foo.value").get().getRawStringContent())
            .isEqualTo(sourceParameter);
        assertThat(rbelElement.findElement("$.blub.value").get().getRawStringContent())
            .isEqualTo("blab");
    }

    @Test
    public void emptyQueryPart_shouldParseCorrectly() {
        final RbelElement rbelElement = RbelLogger.build().getRbelConverter()
            .convertElement("/foobar?", null);

        assertThat(rbelElement.hasFacet(RbelUriFacet.class))
            .isTrue();
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getBasicPath().getRawStringContent())
            .isEqualTo("/foobar");
        assertThat(rbelElement.getFacetOrFail(RbelUriFacet.class).getQueryParameters())
            .isEmpty();
    }
}
