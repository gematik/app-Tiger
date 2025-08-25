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
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.mime.RbelMimeHeaderFacet;
import de.gematik.rbellogger.facets.mime.RbelMimeMessageFacet;
import de.gematik.rbellogger.util.ByteArrayUtils;
import de.gematik.rbellogger.util.EmailConversionUtils;
import eu.europa.esig.dss.spi.DSSUtils;
import java.io.IOException;
import java.util.Optional;
import lombok.SneakyThrows;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;

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
    var signedData = DSSUtils.toCMSSignedData(element.getContent().toInputStream());
    var signedElement = context.convertElement(extractSignedContent(signedData), element);
    var signerInfos = buildSignerInfos(element, context, signedData.getSignerInfos());
    return RbelPkcs7Facet.builder().signed(signedElement).signerInfos(signerInfos).build();
  }

  private RbelElement buildSignerInfos(
      RbelElement element, RbelConversionExecutor context, SignerInformationStore signerInfos) {
    RbelElement signerInfosElement = new RbelElement(null, element);
    return signerInfosElement.addFacet(
        RbelListFacet.builder()
            .childNodes(
                signerInfos.getSigners().stream()
                    .map(signerInfo -> buildSignerInfo(signerInfo, signerInfosElement, context))
                    .toList())
            .build());
  }

  @SneakyThrows
  private RbelElement buildSignerInfo(
      SignerInformation signerInfo, RbelElement element, RbelConversionExecutor context) {

    RbelElement signerInfoElement = new RbelElement(null, element);

    var signedAttributes = signerInfo.getSignedAttributes();
    RbelElement signedAttributesElement = null;
    if (signedAttributes != null) {
      signedAttributesElement =
          EmailConversionUtils.buildAttributesAndExtractRecipientIds(
              signedAttributes, signerInfoElement, context);
    }

    var unsignedAttributes = signerInfo.getUnsignedAttributes();
    RbelElement unsignedAttributesElement = null;
    if (unsignedAttributes != null) {
      unsignedAttributesElement =
          context.convertElement(
              unsignedAttributes.toASN1Structure().getEncoded(), signerInfoElement);
    }

    return signerInfoElement.addFacet(
        RbelPkcs7SignerInfoFacet.builder()
            .contentType(
                OidDictionary.buildAndAddAsn1OidFacet(
                    RbelElement.wrap(signerInfoElement, signerInfo.getContentType())))
            .encryptionAlgorithm(
                OidDictionary.buildAndAddAsn1OidFacet(
                    RbelElement.wrap(signerInfoElement, signerInfo.getEncryptionAlgOID())))
            .digestAlgorithm(
                OidDictionary.buildAndAddAsn1OidFacet(
                    RbelElement.wrap(
                        signerInfoElement, signerInfo.getDigestAlgorithmID().getAlgorithm())))
            .signature(new RbelElement(signerInfo.getSignature(), signerInfoElement))
            .signerId(buildSignerId(signerInfo.getSID(), signerInfoElement, context))
            .counterSignatures(
                buildSignerInfos(signerInfoElement, context, signerInfo.getCounterSignatures()))
            .signedAttributes(signedAttributesElement)
            .unsignedAttributes(unsignedAttributesElement)
            .build());
  }

  @SneakyThrows
  private RbelElement buildSignerId(
      SignerId signerId, RbelElement signerInfoElement, RbelConversionExecutor context) {
    RbelElement signerIdElement = new RbelElement(null, signerInfoElement);
    return signerIdElement.addFacet(
        CmsEntityIdentifierFacet.builder()
            .issuer(context.convertElement(signerId.getIssuer().getEncoded(), signerIdElement))
            .serialNumber(RbelElement.wrap(signerIdElement, signerId.getSerialNumber()))
            .subjectKeyIdentifier(
                context.convertElement(signerId.getSubjectKeyIdentifier(), signerIdElement))
            .build());
  }

  private static byte[] extractSignedContent(CMSSignedData signedData)
      throws IOException, CMSException {
    try {
      return ByteArrayUtils.getBytesFrom(signedData.getSignedContent()::write);
    } catch (CMSException | IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }
}
