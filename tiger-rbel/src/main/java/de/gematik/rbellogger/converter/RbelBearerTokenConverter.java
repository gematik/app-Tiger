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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelBearerTokenFacet;
import de.gematik.rbellogger.util.RbelArrayUtils;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.ArrayUtils;

public class RbelBearerTokenConverter implements RbelConverterPlugin {
  private static final byte[] BEARER_TOKEN_PREFIX = "Bearer ".getBytes(StandardCharsets.UTF_8);

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    var rawContent = rbelElement.getRawContent();
    if (RbelArrayUtils.startsWith(rawContent, BEARER_TOKEN_PREFIX)) {
      final RbelElement bearerTokenElement =
          converter.convertElement(
              ArrayUtils.subarray(rawContent, BEARER_TOKEN_PREFIX.length, rawContent.length),
              rbelElement);
      rbelElement.addFacet(new RbelBearerTokenFacet(bearerTokenElement));
    }
  }
}
