/*
 *
 * Copyright 2025 gematik GmbH
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
import de.gematik.rbellogger.data.facet.RbelB64GzipFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo(onlyActivateFor = "b64gzip")
@Slf4j
public class RbelB64GzipConverter implements RbelConverterPlugin {

  // base 64 encoded gzip prefix
  private final byte[] b64GzipPrefix = "H4s".getBytes();

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    val potentiallyB64Gzip = rbelElement.getContent().startsWith(b64GzipPrefix);
    if (!potentiallyB64Gzip) {
      return;
    }

    parseB64GzippedMessage(rbelElement, converter);
  }

  private void parseB64GzippedMessage(
      final RbelElement parentElement, final RbelConverter converter) {
    try {
      val gzipped = Base64.getDecoder().decode(parentElement.getRawContent());
      val gis = new GZIPInputStream(new java.io.ByteArrayInputStream(gzipped));

      byte[] unzipped = gis.readAllBytes();

      val unzippedElement = converter.convertElement(unzipped, parentElement);
      val rbelB64GzipFacet = new RbelB64GzipFacet(unzippedElement);

      parentElement.addFacet(rbelB64GzipFacet);
    } catch (Exception e) {
      throw new RbelConversionException(e, parentElement, this);
    }
  }
}
