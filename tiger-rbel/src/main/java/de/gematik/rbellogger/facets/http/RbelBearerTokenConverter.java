/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.facets.http;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import java.nio.charset.StandardCharsets;

public class RbelBearerTokenConverter extends RbelConverterPlugin {
  private static final byte[] BEARER_TOKEN_PREFIX = "Bearer ".getBytes(StandardCharsets.UTF_8);

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    var content = rbelElement.getContent();
    if (content.startsWith(BEARER_TOKEN_PREFIX)) {
      final RbelElement bearerTokenElement =
          converter.convertElement(
              content.subArray(BEARER_TOKEN_PREFIX.length, content.size()), rbelElement);
      rbelElement.addFacet(new RbelBearerTokenFacet(bearerTokenElement));
    }
  }
}
