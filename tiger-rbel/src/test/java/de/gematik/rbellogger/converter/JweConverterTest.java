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
package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.facets.jose.RbelJweFacet;
import de.gematik.rbellogger.facets.jose.RbelJwtFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class JweConverterTest {

  @Test
  @SneakyThrows
  void shouldConvertJwe() {
    final RbelLogger rbelConverter =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/ssoTokenFlow.tgr")
                        .build()));
    rbelConverter.getRbelCapturer().initialize();

    final RbelElement postChallengeResponse =
        rbelConverter.getMessageList().stream()
            .filter(e -> e.hasFacet(RbelHttpRequestFacet.class))
            .filter(
                request ->
                    request
                            .getFacet(RbelHttpRequestFacet.class)
                            .get()
                            .getPath()
                            .getRawStringContent()
                            .contains("/sign_response")
                        && request
                            .getFacet(RbelHttpRequestFacet.class)
                            .get()
                            .getMethod()
                            .getRawStringContent()
                            .equals("POST"))
            .findFirst()
            .get();

    assertThat(postChallengeResponse)
        .extractChildWithPath("$..signed_challenge")
        .hasFacet(RbelJweFacet.class)
        .satisfies(
            el -> assertThat(el).extractChildWithPath("$.header").hasFacet(RbelJsonFacet.class),
            el -> assertThat(el).extractChildWithPath("$.body").hasFacet(RbelJwtFacet.class));
  }
}
