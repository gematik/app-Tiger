/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.data.core;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelMessageRenderer;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@Slf4j
public class RbelTcpIpMessageFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(new RbelMessageRenderer());
  }

  private final Long sequenceNumber;
  private final String receivedFromRemoteWithUrl;
  private final RbelElement sender;
  private final RbelElement receiver;

  public static Long getSequenceNumber(RbelElement msg) {
    while (msg.getParentNode() != null) {
      msg = msg.getParentNode();
    }
    return msg.getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getSequenceNumber)
        .orElse(-1L);
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("sender", sender).with("receiver", receiver);
  }

  public Optional<RbelSocketAddress> getSenderHostname() {
    return hostname(sender).map(RbelHostnameFacet::toRbelSocketAddress);
  }

  public Optional<RbelSocketAddress> getReceiverHostname() {
    return hostname(receiver).map(RbelHostnameFacet::toRbelSocketAddress);
  }

  private Optional<RbelHostnameFacet> hostname(RbelElement element) {
    return element.getFacet(RbelHostnameFacet.class);
  }

  public boolean isSameDirectionAs(RbelElement previousMessage) {
    return previousMessage
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(this::compareDirectionWith)
        .orElse(false);
  }

  public boolean compareDirectionWith(RbelTcpIpMessageFacet other) {
    val otherSender = other.getSender().getFacet(RbelHostnameFacet.class).orElse(null);
    val otherReceiver = other.getReceiver().getFacet(RbelHostnameFacet.class).orElse(null);
    val thisSender = this.getSender().getFacet(RbelHostnameFacet.class).orElse(null);
    val thisReceiver = this.getReceiver().getFacet(RbelHostnameFacet.class).orElse(null);

    if (otherSender == null
        || otherReceiver == null
        || thisSender == null
        || thisReceiver == null) {
      return false;
    }

    return (thisSender.domainAndPortEquals(otherSender)
        && thisReceiver.domainAndPortEquals(otherReceiver));
  }
}
