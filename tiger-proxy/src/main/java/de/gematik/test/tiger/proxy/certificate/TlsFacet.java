/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.certificate;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import lombok.Value;

@Value
public class TlsFacet implements RbelFacet {

  private static final String TLS_VERSION = "tlsVersion";

  private static final String CIPHER_SUITE = "cipherSuite";
  private static final String CLIENT_TLS_CERTIFICATE_CHAIN = "clientTlsCertificateChain";

  public static void init() {
    RbelFileWriter.DEFAULT_PRE_SAVE_LISTENER.add(
        (el, json) -> {
          el.getFacet(TlsFacet.class)
              .flatMap(facet -> facet.getTlsVersion().seekValue(String.class))
              .ifPresent(tlsVersion -> json.put(TLS_VERSION, tlsVersion));
          el.getFacet(TlsFacet.class)
              .flatMap(facet -> facet.getCipherSuite().seekValue(String.class))
              .ifPresent(tlsVersion -> json.put(CIPHER_SUITE, tlsVersion));
        });
    RbelFileWriter.DEFAULT_POST_CONVERSION_LISTENER.add(
        (msg, converter, json) -> {
          if (json.has(TLS_VERSION) && json.has(CIPHER_SUITE)) {
            msg.addOrReplaceFacet(
                new TlsFacet(
                    RbelElement.wrap(msg, json.getString(TLS_VERSION)),
                    RbelElement.wrap(msg, json.getString(CIPHER_SUITE)),
                    null));
            msg.getFacet(TracingMessagePairFacet.class)
                .filter(pair -> pair.getResponse() == msg)
                .map(TracingMessagePairFacet::getRequest)
                .ifPresent(
                    req ->
                        req.addOrReplaceFacet(
                            new TlsFacet(
                                RbelElement.wrap(req, json.getString(TLS_VERSION)),
                                RbelElement.wrap(req, json.getString(CIPHER_SUITE)),
                                null)));
          }
        });
  }

  RbelElement tlsVersion;
  RbelElement cipherSuite;
  RbelElement clientCertificateChain;
  RbelMultiMap<RbelElement> childElements;

  public TlsFacet(
      RbelElement tlsVersion, RbelElement cipherSuite, RbelElement clientCertificateChain) {
    this.tlsVersion = tlsVersion;
    this.cipherSuite = cipherSuite;
    this.clientCertificateChain = clientCertificateChain;
    childElements =
        new RbelMultiMap<RbelElement>()
            .with(TLS_VERSION, tlsVersion)
            .with(CIPHER_SUITE, cipherSuite)
            .withSkipIfNull(CLIENT_TLS_CERTIFICATE_CHAIN, clientCertificateChain);
  }
}
