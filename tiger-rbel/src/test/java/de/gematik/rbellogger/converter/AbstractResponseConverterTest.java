/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import org.junit.jupiter.api.BeforeEach;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class AbstractResponseConverterTest {
  protected RbelConverter converter;

  @BeforeEach
  void init() {
    converter = RbelLogger.build().getRbelConverter();
  }

  RbelElement convertMessagePair(String request, String response) {
    var sender = new RbelHostname("host1", 1);
    var receiver = new RbelHostname("host2", 2);
    convertToRbelElement(request, sender, receiver);
    return convertToRbelElement(response, receiver, sender);
  }

  RbelElement convertToRbelElement(String request) {
    var sender = new RbelHostname("host1", 1);
    var receiver = new RbelHostname("host2", 2);
    return convertToRbelElement(request, sender, receiver);
  }

  RbelElement convertToRbelElement(String input, RbelHostname sender, RbelHostname recipient) {
    return converter.parseMessage(
        input.getBytes(StandardCharsets.UTF_8), sender, recipient, Optional.empty());
  }
}
