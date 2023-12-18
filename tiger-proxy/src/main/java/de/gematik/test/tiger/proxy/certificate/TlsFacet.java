/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.certificate;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TlsFacet implements RbelFacet {

  private final RbelElement clientCertificateChain;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    if (clientCertificateChain == null) {
      return new RbelMultiMap<>();
    }
    return new RbelMultiMap<RbelElement>()
        .with("clientTlsCertificateChain", clientCertificateChain);
  }
}
