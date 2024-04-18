/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.file;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
public class BundledServerNameWriterAndReader
    implements RbelFilePreSaveListener, RbelMessagePostProcessor {

  private static final String BUNDLED_HOSTNAME_CLIENT = "bundledHostnameClient";
  private static final String BUNDLED_HOSTNAME_SERVER = "bundledHostnameServer";

  @Override
  public void preSaveCallback(RbelElement rbelElement, JSONObject messageObject) {
    messageObject.put(
        BUNDLED_HOSTNAME_CLIENT,
        extractBundledHostname(rbelElement, RbelTcpIpMessageFacet::getSender));
    messageObject.put(
        BUNDLED_HOSTNAME_SERVER,
        extractBundledHostname(rbelElement, RbelTcpIpMessageFacet::getReceiver));
  }

  @Override
  public void performMessagePostConversionProcessing(
      RbelElement message, RbelConverter converter, JSONObject messageObject) {
    performBundledServerNameExtraction(
        message, messageObject, BUNDLED_HOSTNAME_CLIENT, RbelTcpIpMessageFacet::getSender);
    performBundledServerNameExtraction(
        message, messageObject, BUNDLED_HOSTNAME_SERVER, RbelTcpIpMessageFacet::getReceiver);
  }

  private static void performBundledServerNameExtraction(
      RbelElement message,
      JSONObject messageObject,
      String jsonKey,
      Function<RbelTcpIpMessageFacet, RbelElement> targetRecipient) {
    if (messageObject.has(jsonKey)) {
      final String bundledServername = messageObject.getString(jsonKey);
      message
          .getFacet(RbelTcpIpMessageFacet.class)
          .map(targetRecipient)
          .ifPresent(
              recepient -> {
                RbelHostnameFacet oldFacet = recepient.getFacet(RbelHostnameFacet.class).orElse(null);
                if (oldFacet != null) {
                  recepient.addOrReplaceFacet(
                      RbelHostnameFacet.builder()
                          .domain(oldFacet.getDomain())
                          .port(oldFacet.getPort())
                          .bundledServerName(
                              Optional.of(RbelElement.wrap(recepient, bundledServername)))
                          .build());
                }
              });
    }
  }

  private static String extractBundledHostname(
      RbelElement rbelElement, Function<RbelTcpIpMessageFacet, RbelElement> targetRecipient) {
    return rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(targetRecipient)
        .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
        .flatMap(RbelHostnameFacet::getBundledServerName)
        .map(RbelElement::getRawStringContent)
        .orElse(null);
  }
}
