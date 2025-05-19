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

package de.gematik.rbellogger.facets.pki.ocsp;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.facets.pki.AbstractX509Converter;
import de.gematik.rbellogger.facets.pki.OidDictionary;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
@ConverterInfo(onlyActivateFor = "OCSP")
public class RbelOcspResponseConverter extends AbstractX509Converter {

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    if (!tryConversion(element, context, element::getRawContent)) {
      if (!tryConversion(
          element, context, () -> Base64.getDecoder().decode(element.getRawContent()))) {
        tryConversion(
            element, context, () -> Base64.getUrlDecoder().decode(element.getRawContent()));
      }
    }
  }

  private boolean tryConversion(
      RbelElement element,
      RbelConversionExecutor context,
      Supplier<byte[]> binaryContentExtractor) {
    try {
      val ocspResponse = new OCSPResp(binaryContentExtractor.get());

      val ocspResponseFacet = new RbelOcspResponseFacet();
      int status = ocspResponse.getStatus();
      ocspResponseFacet.setResponseStatus(RbelElement.wrap(element, status));

      BasicOCSPResp basicResponse = (BasicOCSPResp) ocspResponse.getResponseObject();
      executeSafe(
          () ->
              ocspResponseFacet.setVersion(RbelElement.wrap(element, basicResponse.getVersion())));
      executeSafe(
          () ->
              ocspResponseFacet.setResponderId(
                  RbelElement.wrap(
                      element,
                      basicResponse.getResponderId().toASN1Primitive().getName().toString())));
      ocspResponseFacet.setSignatureAlgorithm(
          OidDictionary.buildAndAddAsn1OidFacet(
              RbelElement.wrap(element, basicResponse.getSignatureAlgOID().getId())));

      executeSafe(
          () ->
              ocspResponseFacet.setProducedAt(
                  RbelElement.wrap(
                      element,
                      LocalDateTime.ofInstant(
                          basicResponse.getProducedAt().toInstant(), ZoneId.systemDefault()))));

      executeSafe(
          () ->
              ocspResponseFacet.setResponses(
                  RbelListFacet.wrap(
                      element,
                      el ->
                          Stream.of(basicResponse.getResponses())
                              .map(
                                  singleResp -> buildSingleResponseElement(singleResp, el, context))
                              .toList(),
                      null)));
      executeSafe(
          () ->
              ocspResponseFacet.setExtensions(
                  RbelListFacet.wrap(
                      element,
                      listElement ->
                          basicResponse.getExtensionOIDs().stream()
                              .filter(ASN1ObjectIdentifier.class::isInstance)
                              .map(oid -> basicResponse.getExtension((ASN1ObjectIdentifier) oid))
                              .map(ex -> parseExtension((Extension) ex, listElement, context))
                              .toList(),
                      null)));
      element.addFacet(ocspResponseFacet);
      element.addFacet(new RbelRootFacet<>(ocspResponseFacet));
      return true;
    } catch (Exception e) {
      // swallow
      return false;
    }
  }

  private RbelElement buildSingleResponseElement(
      SingleResp response, RbelElement parentElement, RbelConversionExecutor context) {
    RbelElement result = new RbelElement(null, parentElement);
    final RbelSingleOcspResponseFacet responseFacet = new RbelSingleOcspResponseFacet();
    result.addFacet(responseFacet);
    result.addFacet(new RbelRootFacet<>(responseFacet));

    executeSafe(
        () ->
            responseFacet.setHashAlgorithm(
                RbelElement.wrap(result, response.getCertID().getHashAlgOID().getId())));
    executeSafe(
        () ->
            responseFacet.setIssuerNameHash(
                RbelElement.wrap(
                    result,
                    Hex.toHexString(response.getCertID().getIssuerNameHash()).toUpperCase())));
    executeSafe(
        () ->
            responseFacet.setIssuerKeyHash(
                RbelElement.wrap(
                    result,
                    Hex.toHexString(response.getCertID().getIssuerKeyHash()).toUpperCase())));
    executeSafe(
        () ->
            responseFacet.setSerialNumber(
                RbelElement.wrap(
                    result, response.getCertID().getSerialNumber().toString(16).toUpperCase())));
    executeSafe(
        () ->
            responseFacet.setCertStatus(
                RbelElement.wrap(result, response.getCertStatus().toString())));
    executeSafe(
        () ->
            responseFacet.setExtensions(
                RbelListFacet.wrap(
                    result,
                    listElement ->
                        response.getExtensionOIDs().stream()
                            .filter(ASN1ObjectIdentifier.class::isInstance)
                            .map(oid -> response.getExtension((ASN1ObjectIdentifier) oid))
                            .map(ex -> parseExtension((Extension) ex, listElement, context))
                            .toList(),
                    null)));

    return result;
  }

  private void executeSafe(Runnable runnable) {
    try {
      runnable.run();
    } catch (RuntimeException e) {
      log.trace("Error while executing runnable", e);
    }
  }
}
