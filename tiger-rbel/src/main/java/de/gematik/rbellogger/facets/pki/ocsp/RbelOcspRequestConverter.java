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
package de.gematik.rbellogger.facets.pki.ocsp;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.facets.pki.AbstractX509Converter;
import de.gematik.rbellogger.facets.pki.OidDictionary;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ocsp.OCSPRequest;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
@ConverterInfo(onlyActivateFor = "OCSP")
public class RbelOcspRequestConverter extends AbstractX509Converter {

  public boolean tryConversion(
      RbelElement element,
      RbelConversionExecutor context,
      Supplier<InputStream> binaryContentExtractor) {
    try {
      val ocspRequest =
          new OCSPReq(
              OCSPRequest.getInstance(
                  new ASN1InputStream(binaryContentExtractor.get()).readObject()));
      val ocspRequestFacet = new RbelOcspRequestFacet();

      // OCSPReq does not have getVersion(), so we use getRequestorName and getSignatureAlgOID
      executeSafe(
          () ->
              ocspRequestFacet.setRequestorName(
                  RbelElement.wrap(
                      element,
                      ocspRequest.getRequestorName() != null
                          ? ocspRequest.getRequestorName().toString()
                          : "")));
      executeSafe(
          () ->
              ocspRequestFacet.setSignatureAlgorithm(
                  OidDictionary.buildAndAddAsn1OidFacet(
                      RbelElement.wrap(element, ocspRequest.getSignatureAlgOID().getId()))));

      // Requests
      executeSafe(
          () ->
              ocspRequestFacet.setRequests(
                  RbelListFacet.wrap(
                      element,
                      el ->
                          Stream.of(ocspRequest.getRequestList())
                              .map(req -> buildSingleRequestElement(req, el, context))
                              .toList(),
                      null)));

      // Extensions
      executeSafe(
          () ->
              ocspRequestFacet.setExtensions(
                  RbelListFacet.wrap(
                      element,
                      listElement ->
                          ((List<ASN1ObjectIdentifier>) ocspRequest.getExtensionOIDs())
                              .stream()
                                  .map(ocspRequest::getExtension)
                                  .map(ex -> parseExtension(ex, listElement, context))
                                  .toList(),
                      null)));

      element.addFacet(ocspRequestFacet);
      return true;
    } catch (Exception e) {
      // swallow
      return false;
    }
  }

  private RbelElement buildSingleRequestElement(
      Req request, RbelElement parentElement, RbelConversionExecutor context) {
    RbelElement result = new RbelElement(null, parentElement);
    final RbelSingleOcspRequestFacet requestFacet = new RbelSingleOcspRequestFacet();
    result.addFacet(requestFacet);

    executeSafe(
        () ->
            requestFacet.setHashAlgorithm(
                RbelElement.wrap(result, request.getCertID().getHashAlgOID().getId())));
    executeSafe(
        () ->
            requestFacet.setIssuerNameHash(
                RbelElement.wrap(
                    result,
                    Hex.toHexString(request.getCertID().getIssuerNameHash()).toUpperCase())));
    executeSafe(
        () ->
            requestFacet.setIssuerKeyHash(
                RbelElement.wrap(
                    result,
                    Hex.toHexString(request.getCertID().getIssuerKeyHash()).toUpperCase())));
    executeSafe(
        () ->
            requestFacet.setSerialNumber(
                RbelElement.wrap(
                    result, request.getCertID().getSerialNumber().toString(16).toUpperCase())));
    // Single request extensions
    executeSafe(
        () ->
            requestFacet.setSingleRequestExtensions(
                RbelListFacet.wrap(
                    result,
                    listElement -> {
                      if (request.getSingleRequestExtensions() != null) {
                        return Stream.of(request.getSingleRequestExtensions().getExtensionOIDs())
                            .map(oid -> request.getSingleRequestExtensions().getExtension(oid))
                            .map(ex -> parseExtension(ex, listElement, context))
                            .toList();
                      } else {
                        return java.util.Collections.emptyList();
                      }
                    },
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
