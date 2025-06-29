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
package de.gematik.rbellogger.facets.pki.base64;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.util.RbelContent;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo(onlyActivateFor = "b64gzip")
@Slf4j
public class RbelB64GzipConverter extends RbelConverterPlugin {

  // base 64 encoded gzip prefix
  private final byte[] b64GzipPrefix = "H4s".getBytes();

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    val potentiallyB64Gzip = rbelElement.getContent().startsWith(b64GzipPrefix);
    if (!potentiallyB64Gzip) {
      return;
    }

    parseB64GzippedMessage(rbelElement, converter);
  }

  private void parseB64GzippedMessage(
      final RbelElement parentElement, final RbelConversionExecutor converter) {
    try {
      val gzipped = Base64.getDecoder().wrap(parentElement.getContent().toInputStream());
      val gis = new GZIPInputStream(gzipped);

      var unzipped = RbelContent.from(gis);

      val unzippedElement = converter.convertElement(unzipped, parentElement);
      val rbelB64GzipFacet = new RbelB64GzipFacet(unzippedElement);

      parentElement.addFacet(rbelB64GzipFacet);
    } catch (Exception e) {
      throw new RbelConversionException(e, parentElement, this);
    }
  }
}
