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

package de.gematik.rbellogger.file;

import static de.gematik.rbellogger.file.RbelFileWriter.RECEIVER_HOSTNAME;
import static de.gematik.rbellogger.file.RbelFileWriter.SENDER_HOSTNAME;
import static de.gematik.rbellogger.file.RbelFileWriter.SEQUENCE_NUMBER;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import org.json.JSONObject;

public class TcpIpMessageFacetWriter implements RbelFilePreSaveListener {

  @Override
  public void preSaveCallback(RbelElement rbelElement, JSONObject jsonObject) {
    jsonObject.put(
        SENDER_HOSTNAME,
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getSender)
            .flatMap(element -> element.getFacet(RbelHostnameFacet.class))
            .map(RbelHostnameFacet::toString)
            .orElse(""));
    jsonObject.put(
        RECEIVER_HOSTNAME,
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getReceiver)
            .flatMap(element -> element.getFacet(RbelHostnameFacet.class))
            .map(RbelHostnameFacet::toString)
            .orElse(""));
    jsonObject.put(
        SEQUENCE_NUMBER,
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getSequenceNumber)
            .map(Object::toString)
            .orElse(""));
  }
}
