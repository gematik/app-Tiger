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
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.pki.CmsEntityIdentifierFacet;
import de.gematik.rbellogger.facets.pki.OidDictionary;
import de.gematik.rbellogger.facets.pop3.RbelPop3Command;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseConverter;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseFacet;
import de.gematik.rbellogger.util.EmailConversionUtils;
import de.gematik.rbellogger.util.email_crypto.EmailDecryption;
import java.io.IOException;
import java.util.Optional;
import lombok.SneakyThrows;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.KeyTransRecipientId;
import org.bouncycastle.cms.RecipientInformation;

@ConverterInfo(onlyActivateFor = "mime")
public class RbelCmsEnvelopedDataConverter extends RbelConverterPlugin {

  @Override
  @SneakyThrows
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (rbelElement.getContent().isEmpty() || isMimeBodyOfPop3TopResponse(rbelElement, converter)) {
      return;
    }
    if (Optional.ofNullable(rbelElement.getParentNode())
        .flatMap(node -> node.getFacet(RbelMimeMessageFacet.class))
        .map(RbelMimeMessageFacet::header)
        .flatMap(header -> header.getFacet(RbelMimeHeaderFacet.class))
        .map(header -> header.get("content-type"))
        .map(RbelElement::getRawStringContent)
        .filter(contentType -> contentType.contains("smime-type=authenticated-enveloped-data"))
        .isPresent()) {
      try {
        RbelCmsEnvelopedDataFacet facet = parseEncryptedMessage(rbelElement, converter);
        rbelElement.addFacet(facet);
      } catch (CMSException e) {
        throw new RbelConversionException(e, rbelElement, this);
      }
    }
  }

  private static boolean isMimeBodyOfPop3TopResponse(
      RbelElement rbelElement, RbelConversionExecutor converter) {
    return Optional.ofNullable(rbelElement.getParentNode())
        .filter(parent -> parent.hasFacet(RbelMimeMessageFacet.class))
        .map(RbelElement::getParentNode)
        .filter(mimeContainer -> mimeContainer.hasFacet(RbelPop3ResponseFacet.class))
        .flatMap(pop3Response -> RbelPop3ResponseConverter.findPop3Command(pop3Response, converter))
        .filter(RbelPop3Command.TOP::equals)
        .isPresent();
  }

  @SneakyThrows
  private RbelCmsEnvelopedDataFacet parseEncryptedMessage(
      RbelElement element, RbelConversionExecutor context) throws CMSException {
    var keyManager = context.getRbelKeyManager();

    var decryptedMessage =
        EmailDecryption.decrypt(element.getContent(), keyManager)
            .orElseThrow(
                () -> new RbelConversionException("Could not decrypt content", element, this));
    var envelopedData = new CMSAuthEnvelopedData(element.getContent().toInputStream());
    var unauthAttributes = envelopedData.getUnauthAttrs();
    var authAttributes = envelopedData.getAuthAttrs();

    var decryptedMessageElement = context.convertElement(decryptedMessage, element);
    var unauthAttributesElement =
        EmailConversionUtils.buildAttributesAndExtractRecipientIds(
            unauthAttributes, element, context);
    var authAttributesElement =
        authAttributes != null
            ? context.convertElement(authAttributes.toASN1Structure().getEncoded(), element)
            : null;

    var recipientInfos =
        RbelListFacet.wrap(
            element,
            list ->
                envelopedData.getRecipientInfos().getRecipients().stream()
                    .map(recipientInfo -> buildRecipientInfo(recipientInfo, list, context))
                    .toList(),
            null);

    return RbelCmsEnvelopedDataFacet.builder()
        .decrypted(decryptedMessageElement)
        .recipientInfos(recipientInfos)
        .authAttributes(authAttributesElement)
        .unauthAttributes(unauthAttributesElement)
        .build();
  }

  @SneakyThrows
  private RbelElement buildRecipientInfo(
      RecipientInformation recipientInfo, RbelElement element, RbelConversionExecutor context) {
    RbelElement recipientInfoElement = new RbelElement(null, element);
    var contentType =
        OidDictionary.buildAndAddAsn1OidFacet(
            RbelElement.wrap(recipientInfoElement, recipientInfo.getContentType()));
    var keyEncryptionAlgorithm =
        OidDictionary.buildAndAddAsn1OidFacet(
            RbelElement.wrap(
                recipientInfoElement, recipientInfo.getKeyEncryptionAlgorithm().getAlgorithm()));
    var recipientId = buildRecipientId(recipientInfo, context, recipientInfoElement);

    return recipientInfoElement.addFacet(
        RbelRecipientInfoFacet.builder()
            .contentType(contentType)
            .keyEncryptionAlgorithm(keyEncryptionAlgorithm)
            .recipientId(recipientId)
            .build());
  }

  private RbelElement buildRecipientId(
      RecipientInformation recipientInfo,
      RbelConversionExecutor context,
      RbelElement recipientInfoElement)
      throws IOException {
    if (recipientInfo.getRID() instanceof KeyTransRecipientId keyTransRecipientId) {
      return buildKeyTransRecipientId(recipientInfoElement, keyTransRecipientId, context);
    } else {
      return null;
    }
  }

  private RbelElement buildKeyTransRecipientId(
      RbelElement recipientInfoElement,
      KeyTransRecipientId keyTransRecipientId,
      RbelConversionExecutor context)
      throws IOException {
    RbelElement recipientIdElement = new RbelElement(null, recipientInfoElement);
    return recipientIdElement.addFacet(
        CmsEntityIdentifierFacet.builder()
            .issuer(
                context.convertElement(
                    keyTransRecipientId.getIssuer().getEncoded(), recipientIdElement))
            .serialNumber(
                RbelElement.wrap(recipientIdElement, keyTransRecipientId.getSerialNumber()))
            .subjectKeyIdentifier(
                context.convertElement(
                    keyTransRecipientId.getSubjectKeyIdentifier(), recipientIdElement))
            .build());
  }
}
