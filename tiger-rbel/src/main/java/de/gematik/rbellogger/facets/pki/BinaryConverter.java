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
package de.gematik.rbellogger.facets.pki;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.data.RbelElement;
import java.io.InputStream;
import java.util.Base64;
import java.util.function.Supplier;
import lombok.val;

public interface BinaryConverter {
  boolean tryConversion(
      RbelElement element,
      RbelConversionExecutor context,
      Supplier<InputStream> binaryContentExtractor);

  static void consumeElement(
      BinaryConverter converter, final RbelElement element, final RbelConversionExecutor context) {
    val content = element.getContent();
    if (!converter.tryConversion(element, context, content::toInputStream)
        && !converter.tryConversion(
            element, context, () -> Base64.getDecoder().wrap(content.toInputStream()))) {
      converter.tryConversion(
          element, context, () -> Base64.getUrlDecoder().wrap(content.toInputStream()));
    }
  }
}
