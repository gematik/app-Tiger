/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelMapFacet;
import de.gematik.rbellogger.data.facet.RbelValueFacet;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.style.X500NameTokenizer;
import org.bouncycastle.jce.X509Principal;

@Slf4j
public class RbelX500Converter implements RbelConverterPlugin {
  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    try {
      if (element.getFacets().isEmpty()) {
        var rdnMap = new RbelMultiMap<RbelElement>();
        final X500NameTokenizer nameTokenizer =
            new X500NameTokenizer(new X509Principal(element.getRawContent()).toString());
        while (nameTokenizer.hasMoreTokens()) {
          var token = nameTokenizer.nextToken().split("=");
          rdnMap.put(token[0], context.convertElement(token[1], element));
        }
        if (!rdnMap.isEmpty()) {
          element.addFacet(new RbelMapFacet(rdnMap));
          element.addFacet(
              new RbelValueFacet<>(new X509Principal(element.getRawContent()).toString()));
        }
      }
    } catch (RuntimeException | IOException e) {
      // swallow
    }
  }
}
