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
package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.MockserverX509CertificateWrapper;
import de.gematik.test.tiger.proxy.certificate.TlsFacet;
import de.gematik.test.tiger.proxy.handler.BundledServerNamesAdder;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

@ConverterInfo(addAutomatically = false)
@RequiredArgsConstructor
public class TigerProxyPairingConverter extends RbelConverterPlugin {

  private final BundledServerNamesAdder bundledServerNamesAdder = new BundledServerNamesAdder();

  @Override
  public synchronized void consumeElement(
      RbelElement rbelElement, RbelConversionExecutor converter) {
    if (rbelElement.getParentNode() != null) {
      return;
    }
    val requestFacet = rbelElement.getFacet(MockServerRequestFacet.class);
    val responseFacet = rbelElement.getFacet(MockServerResponseFacet.class);
    if (rbelElement.hasFacet(RbelHttpRequestFacet.class) && requestFacet.isPresent()) {
      parseCertificateChainIfPresent(requestFacet.get().getHttpRequest(), rbelElement, converter);
      rbelElement.removeFacetsOfType(MockServerRequestFacet.class);

      addHostnames(rbelElement);
    } else if (rbelElement.hasFacet(RbelHttpResponseFacet.class)) {
      if (responseFacet.isPresent()) {
        parseCertificateChainIfPresent(
            responseFacet.get().getHttpRequest(), rbelElement, converter);

        // Critical point: here we pair the request and response, without the request being
        // necessarily parsed completely
        addPairingFacet(
            rbelElement, responseFacet.get().getHttpRequest().getCorrespondingRbelMessage());
        rbelElement.removeFacetsOfType(MockServerRequestFacet.class);
      } else {
        // fallback: look for corresponding request manually
        converter
            .getPreviousMessagesInSameConnectionAs(rbelElement)
            .findFirst()
            .filter(req -> req.hasFacet(RbelHttpRequestFacet.class))
            .ifPresent(req -> addPairingFacet(rbelElement, req));
      }
      addHostnames(rbelElement);
    }
  }

  private void addPairingFacet(RbelElement response, RbelElement request) {
    response.addOrReplaceFacet(new TracingMessagePairFacet(response, request));
    request.addOrReplaceFacet(new TracingMessagePairFacet(response, request));
  }

  private void parseCertificateChainIfPresent(
      HttpRequest httpRequest, RbelElement message, RbelConversionExecutor rbelConverter) {
    if (StringUtils.isBlank(httpRequest.getTlsVersion())) {
      return;
    }
    RbelElement certificateChainElement = null;
    if (httpRequest.getClientCertificateChain() != null
        && !httpRequest.getClientCertificateChain().isEmpty()) {
      certificateChainElement = parseCertificateChain(httpRequest, message, rbelConverter);
    }
    message.addFacet(
        new TlsFacet(
            RbelElement.wrap(message, httpRequest.getTlsVersion()),
            RbelElement.wrap(message, httpRequest.getCipherSuite()),
            certificateChainElement));
  }

  private RbelElement parseCertificateChain(
      HttpRequest httpRequest, RbelElement message, RbelConversionExecutor rbelConverter) {
    val chain = new RbelElement(null, message);
    chain.addFacet(
        RbelListFacet.builder()
            .childNodes(
                httpRequest.getClientCertificateChain().stream()
                    .map(MockserverX509CertificateWrapper::certificate)
                    .map(cert -> mapToRbelElement(cert, chain, rbelConverter))
                    .toList())
            .build());
    return chain;
  }

  private void addHostnames(RbelElement element) {
    bundledServerNamesAdder.addBundledServerNameToHostnameFacet(element);
  }

  public static RbelElement mapToRbelElement(
      Certificate certificate, RbelElement parentNode, RbelConversionExecutor converter) {
    try {
      final RbelElement certificateNode = new RbelElement(certificate.getEncoded(), parentNode);
      converter.convertElement(certificateNode);
      return certificateNode;
    } catch (CertificateEncodingException e) {
      final RbelElement rbelElement = new RbelElement(null, parentNode);
      rbelElement.addFacet(
          RbelNoteFacet.builder()
              .style(NoteStyling.ERROR)
              .value(
                  "Error while trying to get binary representation for certificate: "
                      + e.getMessage())
              .build());
      return rbelElement;
    }
  }
}
