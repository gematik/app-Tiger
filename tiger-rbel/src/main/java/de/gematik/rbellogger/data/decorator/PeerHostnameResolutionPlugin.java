/*
 *
 * Copyright 2026 gematik GmbH
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
package de.gematik.rbellogger.data.decorator;

import static de.gematik.rbellogger.data.core.RbelSocketAddressFacet.buildRbelSocketAddressFacet;

import com.google.common.base.Suppliers;
import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.RbelMessageMetadata.RbelMetadataValue;
import de.gematik.rbellogger.data.core.RbelSocketAddressFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.file.IncompleteMessageReadFromFile;
import de.gematik.rbellogger.util.RbelInternetAddress;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.rbellogger.util.ReverseHostnameResolver;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Names the sender and receiver of a recorded message via reverse DNS if only their IP is known,
 * e.g. a client container connecting to the Tiger proxy. Runs while parsing, so forwarding never
 * waits for it. Messages read from files keep their addresses as written.
 */
public class PeerHostnameResolutionPlugin extends RbelConverterPlugin {

  // created on first use, so that neither the configuration nor the lookup threads are touched
  // while lookups are switched off
  private final Supplier<ReverseHostnameResolver> resolver;

  public PeerHostnameResolutionPlugin() {
    this.resolver = Suppliers.memoize(ReverseHostnameResolver::fromConfiguration);
  }

  PeerHostnameResolutionPlugin(ReverseHostnameResolver resolver) {
    this.resolver = () -> resolver;
  }

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PREPARATION;
  }

  @Override
  public int getPriority() {
    // after the plugins that set sender and receiver from forwarded metadata
    return -100;
  }

  @Override
  public void consumeElement(RbelElement message, RbelConversionExecutor converter) {
    if (message.getParentNode() != null
        || message.hasFacet(IncompleteMessageReadFromFile.class)
        || !ReverseHostnameResolver.isActive()) {
      return;
    }
    final Optional<RbelMessageMetadata> metadata = message.getFacet(RbelMessageMetadata.class);
    final Optional<RbelTcpIpMessageFacet> tcpIpFacet =
        message.getFacet(RbelTcpIpMessageFacet.class);
    if (metadata.isEmpty() || tcpIpFacet.isEmpty()) {
      return;
    }
    final Optional<RbelSocketAddress> sender =
        name(metadata.get(), RbelMessageMetadata.MESSAGE_SENDER);
    final Optional<RbelSocketAddress> receiver =
        name(metadata.get(), RbelMessageMetadata.MESSAGE_RECEIVER);
    if (sender.isEmpty() && receiver.isEmpty()) {
      return;
    }
    final RbelTcpIpMessageFacet.RbelTcpIpMessageFacetBuilder builder = tcpIpFacet.get().toBuilder();
    sender.ifPresent(
        address -> builder.sender(rebuild(message, address, tcpIpFacet.get().getSender())));
    receiver.ifPresent(
        address -> builder.receiver(rebuild(message, address, tcpIpFacet.get().getReceiver())));
    message.addOrReplaceFacet(builder.build());
  }

  private Optional<RbelSocketAddress> name(
      RbelMessageMetadata metadata, RbelMetadataValue<RbelSocketAddress> key) {
    final Optional<RbelSocketAddress> named =
        key.getValue(metadata)
            .filter(address -> address.getAddress() != null)
            .filter(address -> address.getAddress().getHostname() == null)
            .filter(address -> address.getAddress().getIpAddress() != null)
            .filter(address -> !address.isLoopbackAddress())
            .flatMap(this::withReverseName);
    named.ifPresent(address -> key.putValue(metadata, address));
    return named;
  }

  private Optional<RbelSocketAddress> withReverseName(RbelSocketAddress address) {
    final byte[] ip = address.getAddress().getIpAddress();
    return address
        .getAddress()
        .toInetAddress()
        .flatMap(resolver.get()::lookup)
        .map(name -> new RbelSocketAddress(new RbelInternetAddress(name, ip), address.getPort()));
  }

  private static RbelElement rebuild(
      RbelElement message, RbelSocketAddress address, RbelElement previous) {
    final String bundledServerName =
        Optional.ofNullable(previous)
            .flatMap(element -> element.getFacet(RbelSocketAddressFacet.class))
            .flatMap(RbelSocketAddressFacet::getBundledServerName)
            .flatMap(RbelElement::printValue)
            .orElse(null);
    return buildRbelSocketAddressFacet(message, address, bundledServerName);
  }
}
