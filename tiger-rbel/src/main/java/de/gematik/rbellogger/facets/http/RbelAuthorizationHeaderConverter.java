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
package de.gematik.rbellogger.facets.http;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.val;

public class RbelAuthorizationHeaderConverter extends RbelConverterPlugin {
  private static final byte[] BEARER_TOKEN_PREFIX = "Bearer ".getBytes(StandardCharsets.UTF_8);
  private static final byte[] DPOP_TOKEN_PREFIX = "DPoP ".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BASIC_PREFIX = "Basic ".getBytes(StandardCharsets.UTF_8);

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    var content = rbelElement.getContent();
    if (content.startsWith(BEARER_TOKEN_PREFIX)) {
      val bearerTokenElement =
          RbelElement.create(
              content.subArray(BEARER_TOKEN_PREFIX.length, content.size()), rbelElement);
      rbelElement.addFacet(new RbelBearerTokenFacet(bearerTokenElement));
      converter.convertElement(bearerTokenElement);
    } else if (content.startsWith(DPOP_TOKEN_PREFIX)) {
      val dpopTokenElement =
          RbelElement.create(
              content.subArray(DPOP_TOKEN_PREFIX.length, content.size()), rbelElement);
      rbelElement.addFacet(new RbelDpopTokenFacet(dpopTokenElement));
      converter.convertElement(dpopTokenElement);
    } else if (content.startsWith(BASIC_PREFIX)) {
      val cleartextContent =
          RbelContent.of(
              Base64.getDecoder()
                  .decode(content.subArray(BASIC_PREFIX.length, content.size()).toByteArray()));
      val colonPosition = cleartextContent.indexOf((byte) ':');
      if (colonPosition != -1) {
        val username = RbelElement.create(cleartextContent.subArray(0, colonPosition), rbelElement);
        val password =
            RbelElement.create(
                cleartextContent.subArray(colonPosition + 1, cleartextContent.size()), rbelElement);
        rbelElement.addFacet(new RbelBasicAuthorizationFacet(username, password));
        converter.convertElement(password);
        converter.convertElement(username);
      } else {
        rbelElement.addFacet(
            RbelNoteFacet.builder()
                .style(NoteStyling.WARN)
                .value("Could not find colon in Basic Authorization String")
                .build());
      }
    }
  }
}
