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

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;

/**
 * Represents a possible interpretation of a RbelElement. This can be both an interpretation of the
 * actual data present in the RbelElement or metadata.
 */
public interface RbelFacet {

  /**
   * Child elements for this facet. The content of the child elements should always be directly or
   * indirectly be taken from the parent element of this facet (e.g. decrypted data is acceptable).
   *
   * @return A map containing all child elements along with their name (key)
   */
  RbelMultiMap<RbelElement> getChildElements();

  /**
   * When parsing a message this gives feedback if we can expect a paired response-message to be
   * transmitted. (would be true for an HTTP-Request, false for HTTP-Response, false for a
   * STOMP-message...). Will consequently be false for all non-message-protocols. (When we encounter
   * a stray JSON-message directly transmitted via TCP we should not expect the server to send a
   * reply).
   *
   * @return Whether we should expect a reply message.
   */
  default boolean shouldExpectReplyMessage() {
    return false;
  }
}
