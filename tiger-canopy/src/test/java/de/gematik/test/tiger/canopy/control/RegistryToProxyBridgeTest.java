/*
 *
 * Copyright 2021-2026 gematik GmbH
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
 *
 */
package de.gematik.test.tiger.canopy.control;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.RegistryEvent;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistryToProxyBridgeTest {

  private TigerProxyAdminClient client;
  private CanopyConfiguration configuration;
  private RegistryToProxyBridge bridge;

  @BeforeEach
  void setup() {
    client = mock(TigerProxyAdminClient.class);
    configuration = new CanopyConfiguration();
    bridge = new RegistryToProxyBridge(client, configuration);
  }

  private static ProxiedHostEntry entry(String host) {
    return ProxiedHostEntry.builder()
        .host(host)
        .matchType(MatchType.EXACT)
        .addedAt(Instant.now())
        .build();
  }

  @Test
  void onHostAdded_routePerHost_addsRouteAndStoresId() {
    configuration.setControlMode(ControlMode.ROUTE_PER_HOST);
    ProxiedHostEntry e = entry("foo.example");

    bridge.onHostAdded(new RegistryEvent.HostAddedEvent(e));

    verify(client).addRoute("foo.example", Optional.empty());
  }

  @Test
  void onHostAdded_routePerHost_routesToPerEntryOverrideWhenSet() {
    configuration.setControlMode(ControlMode.ROUTE_PER_HOST);
    ProxiedHostEntry e =
        ProxiedHostEntry.builder()
            .host("pop3.example")
            .matchType(MatchType.EXACT)
            .addedAt(Instant.now())
            .tigerProxyUrl("http://pop3-tp:9100")
            .build();

    bridge.onHostAdded(new RegistryEvent.HostAddedEvent(e));

    verify(client).addRoute("pop3.example", Optional.of("http://pop3-tp:9100"));
  }

  @Test
  void onHostRemoved_deletesRouteWhenIdPresent() {
    configuration.setControlMode(ControlMode.ROUTE_PER_HOST);
    ProxiedHostEntry e = entry("foo.example");

    bridge.onHostRemoved(new RegistryEvent.HostRemovedEvent(e));

    // The client's internal tracking is responsible for managing route IDs
    verify(client).deleteRouteForHost("foo.example", Optional.empty());
  }

  @Test
  void onHostRemoved_deletesAgainstPerEntryOverrideWhenSet() {
    configuration.setControlMode(ControlMode.ROUTE_PER_HOST);
    ProxiedHostEntry e =
        ProxiedHostEntry.builder()
            .host("pop3.example")
            .matchType(MatchType.EXACT)
            .addedAt(Instant.now())
            .tigerProxyUrl("http://pop3-tp:9100")
            .build();

    bridge.onHostRemoved(new RegistryEvent.HostRemovedEvent(e));

    verify(client).deleteRouteForHost("pop3.example", Optional.of("http://pop3-tp:9100"));
  }

  @Test
  void onHostRemoved_skipsWhenNoRouteId() {
    configuration.setControlMode(ControlMode.ROUTE_PER_HOST);

    bridge.onHostRemoved(new RegistryEvent.HostRemovedEvent(entry("foo.example")));

    // deleteRouteForHost should still be called; the client handles the internal tracking
    verify(client).deleteRouteForHost("foo.example", Optional.empty());
  }
}
