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

import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.RegistryEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@link RegistryEvent}s and propagates them to the configured Tiger proxy via {@link
 * TigerProxyAdminClient}. Active when {@code canopy.controlMode != NONE}.
 *
 * <p>For {@link ControlMode#ROUTE_PER_HOST} every {@link RegistryEvent.HostAddedEvent} triggers a
 * {@code PUT /route}. Route IDs are tracked internally by the client for cleanup on removal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${canopy.controlMode:NONE}' != 'NONE'")
public class RegistryToProxyBridge {

  private final TigerProxyAdminClient client;
  private final CanopyConfiguration configuration;

  @EventListener
  public void onHostAdded(RegistryEvent.HostAddedEvent event) {
    ProxiedHostEntry entry = event.entry();
    if (configuration.getControlMode() != ControlMode.ROUTE_PER_HOST) {
      log.atDebug()
          .addArgument(entry::getHost)
          .addArgument(configuration::getControlMode)
          .log("Bridge ignoring HostAddedEvent for '{}' (controlMode={})");
      return;
    }
    client.addRoute(entry.getHost(), Optional.ofNullable(entry.getTigerProxyUrl()));
  }

  @EventListener
  public void onHostRemoved(RegistryEvent.HostRemovedEvent event) {
    ProxiedHostEntry entry = event.entry();
    if (configuration.getControlMode() != ControlMode.ROUTE_PER_HOST) {
      return;
    }
    client.deleteRouteForHost(entry.getHost(), Optional.ofNullable(entry.getTigerProxyUrl()));
  }
}
