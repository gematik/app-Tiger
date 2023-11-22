/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.proxy.client;

import static de.gematik.test.tiger.proxy.AbstractTigerProxy.PAIRED_MESSAGE_UUID;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@RequiredArgsConstructor
@Slf4j
public class ProxyFileReadingFilter implements RbelMessagePostProcessor {

  private final String filterExpression;
  private final Map<String, RbelElement> deletedMessages = new HashMap<>();

  @Override
  public void performMessagePostConversionProcessing(
      RbelElement message, RbelConverter converter, JSONObject messageObject) {
    if (isKeepMessage(message)) {
      if (messageObject.has(PAIRED_MESSAGE_UUID)) {
        RbelElement deletedPartner =
            deletedMessages.get(messageObject.getString(PAIRED_MESSAGE_UUID));
        if (deletedPartner != null) {
          deletedMessages.remove(deletedPartner.getUuid());
        }
      }
    } else {
      if (messageObject.has(PAIRED_MESSAGE_UUID)) {
        final var partnerMessage =
            deletedMessages.get(messageObject.getString(PAIRED_MESSAGE_UUID));
        if (partnerMessage != null) {
          converter.removeMessage(message);
          converter.removeMessage(partnerMessage);
        }
      } else {
        deletedMessages.put(message.getUuid(), message);
      }
    }
  }

  private boolean isKeepMessage(RbelElement message) {
    return TigerJexlExecutor.matchesAsJexlExpression(message, filterExpression);
  }
}
