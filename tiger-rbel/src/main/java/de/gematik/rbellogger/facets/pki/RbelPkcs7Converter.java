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
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.mime.RbelMimeHeaderFacet;
import de.gematik.rbellogger.facets.mime.RbelMimeMessageFacet;
import eu.europa.esig.dss.spi.DSSUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import lombok.SneakyThrows;
import org.bouncycastle.cms.CMSException;

@ConverterInfo(onlyActivateFor = "mime")
public class RbelPkcs7Converter extends RbelConverterPlugin {

  @Override
  @SneakyThrows
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (Optional.ofNullable(rbelElement.getParentNode())
        .flatMap(node -> node.getFacet(RbelMimeMessageFacet.class))
        .map(RbelMimeMessageFacet::header)
        .flatMap(header -> header.getFacet(RbelMimeHeaderFacet.class))
        .map(header -> header.get("content-type"))
        .map(RbelElement::getRawStringContent)
        .filter(contentType -> contentType.contains("smime-type=signed-data"))
        .isPresent()) {
      try {
        RbelPkcs7Facet facet = parseSignedMessage(rbelElement, converter);
        rbelElement.addFacet(facet);
      } catch (CMSException e) {
        throw new RbelConversionException(e, rbelElement, this);
      }
    }
  }

  private RbelPkcs7Facet parseSignedMessage(RbelElement element, RbelConversionExecutor context)
      throws CMSException, IOException {

    final byte[] signedContent = extractSignedContent(element.getRawContent());

    var signedElement = new RbelElement(signedContent, element);
    context.convertElement(signedElement);

    return RbelPkcs7Facet.builder().signed(signedElement).build();
  }

  private static byte[] extractSignedContent(byte[] signedMessageContent)
      throws IOException, CMSException {
    try (var out = new ByteArrayOutputStream()) {
      DSSUtils.toCMSSignedData(signedMessageContent).getSignedContent().write(out);
      return out.toByteArray();
    }
  }
}
