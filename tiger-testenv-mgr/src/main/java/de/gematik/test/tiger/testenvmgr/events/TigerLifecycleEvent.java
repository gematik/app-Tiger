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
 * Marker interface for lifecycle events published on {@link TigerLifecycleEventBus}. Sealed so the
 * set of events is closed at compile time — out-of-tree extensions subscribe, they do not invent
 * new event types.
 *
 * <p>See {@code doc/adr/canopy-extension-repo-extraction.md} for the design rationale and the list
 * of events.
 */
public sealed interface TigerLifecycleEvent
    permits BeforeServerStartEvent,
        AfterServerStartEvent,
        AfterServerStopEvent,
        BeforeContainerStartEvent {

  /** The server this event refers to. */
  AbstractTigerServer getServer();
}
