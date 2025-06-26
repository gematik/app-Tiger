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
package de.gematik.rbellogger.facets.mime;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.util.email_crypto.EmailDecryption;
import java.util.Optional;
import lombok.SneakyThrows;
import org.bouncycastle.cms.CMSException;

@ConverterInfo(onlyActivateFor = "mime")
public class RbelEncryptedMailConverter extends RbelConverterPlugin {

  @Override
  @SneakyThrows
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (Optional.ofNullable(rbelElement.getParentNode())
        .flatMap(node -> node.getFacet(RbelMimeMessageFacet.class))
        .map(RbelMimeMessageFacet::header)
        .flatMap(header -> header.getFacet(RbelMimeHeaderFacet.class))
        .map(header -> header.get("content-type"))
        .map(RbelElement::getRawStringContent)
        .filter(contentType -> contentType.contains("smime-type=authenticated-enveloped-data"))
        .isPresent()) {
      try {
        RbelDecryptedEmailFacet facet = parseEncryptedMessage(rbelElement, converter);
        rbelElement.addFacet(facet);
      } catch (CMSException e) {
        throw new RbelConversionException(e, rbelElement, this);
      }
    }
  }

  private RbelDecryptedEmailFacet parseEncryptedMessage(
      RbelElement element, RbelConversionExecutor context) throws CMSException {
    var keyManager = context.getRbelKeyManager();

    var decryptedMessage =
        EmailDecryption.decrypt(element.getContent(), keyManager)
            .orElseThrow(
                () -> new RbelConversionException("Could not decrypt content", element, this));

    var decryptedMessageElement = new RbelElement(decryptedMessage, element);
    context.convertElement(decryptedMessageElement);

    return RbelDecryptedEmailFacet.builder().decrypted(decryptedMessageElement).build();
  }
}
