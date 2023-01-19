/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.rbellogger.data.facet;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import org.junit.jupiter.api.Test;

class RbelHostnameFacetTest {

    @Test
    void generateRbelHostnameFacet() {
        RbelElement element = RbelHostnameFacet.buildRbelHostnameFacet(new RbelElement(null, null),
            (RbelHostname) RbelHostname.generateFromUrl("https://foo:8080").get());
        assertThat(element.hasFacet(RbelHostnameFacet.class))
            .isTrue();
        assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
            .isEqualTo("foo");
        assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
            .isEqualTo("8080");
    }

    @Test
    void generateRbelHostnameFacet_withoutPortAndHttps() {
        RbelElement element = RbelHostnameFacet.buildRbelHostnameFacet(new RbelElement(null, null),
            (RbelHostname) RbelHostname.generateFromUrl("https://foo").get());
        assertThat(element.hasFacet(RbelHostnameFacet.class))
            .isTrue();
        assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
            .isEqualTo("foo");
        assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
            .isEqualTo("443");
    }

    @Test
    void generateRbelHostnameFacet_withoutPortAndHttp() {
        RbelElement element = RbelHostnameFacet.buildRbelHostnameFacet(new RbelElement(null, null),
            (RbelHostname) RbelHostname.generateFromUrl("http://foo").get());
        assertThat(element.hasFacet(RbelHostnameFacet.class))
            .isTrue();
        assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
            .isEqualTo("foo");
        assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
            .isEqualTo("80");
    }
}
