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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;

class AbstractResponseConverterTest {
  protected RbelConverter converter;

  @BeforeEach
  void init() {
    converter =
        RbelLogger.build(
                new RbelConfiguration().activateConversionFor("pop3").activateConversionFor("mime"))
            .getRbelConverter();
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
        input.getBytes(StandardCharsets.UTF_8),
        new RbelMessageMetadata().withSender(sender).withReceiver(recipient));
  }
}
