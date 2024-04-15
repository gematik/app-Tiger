/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.certificate;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
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
            msg.addFacet(
                new TlsFacet(
                    RbelElement.wrap(msg, json.getString(TLS_VERSION)),
                    RbelElement.wrap(msg, json.getString(CIPHER_SUITE)),
                    null));
            msg.getFacet(TracingMessagePairFacet.class)
                .filter(pair -> pair.getResponse() == msg)
                .map(TracingMessagePairFacet::getRequest)
                .ifPresent(
                    req ->
                        req.addFacet(
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
