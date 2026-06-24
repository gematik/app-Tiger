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
package de.gematik.test.tiger.testenvmgr.events;

import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;

/**
 * Fired by {@code AbstractTigerServer#start} immediately before delegating to {@code
 * performStartup}. Subscribers may use it to register implicit {@code dependsUpon} edges, seed
 * configuration, or otherwise prepare for the server's startup. Mutating the server's configuration
 * at this point is allowed but discouraged — prefer {@link BeforeContainerStartEvent} for
 * container-bound mutations.
 */
public record BeforeServerStartEvent(AbstractTigerServer server) implements TigerLifecycleEvent {
  @Override
  public AbstractTigerServer getServer() {
    return server;
  }
}
