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
package de.gematik.rbellogger.data.facet;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelHostnameFacetTest {

  @ParameterizedTest
  @CsvSource({"https://foo,foo,443", "http://foo,foo,80", "https://foo:8080,foo,8080"})
  void generateRbelHostnameFacet(String url, String hostname, String port) {
    RbelElement element =
        RbelHostnameFacet.buildRbelHostnameFacet(
            new RbelElement(), (RbelHostname) RbelHostname.generateFromUrl(url).get());
    assertThat(element.hasFacet(RbelHostnameFacet.class)).isTrue();
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
        .isEqualTo(hostname);
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
        .isEqualTo(port);
  }
}
