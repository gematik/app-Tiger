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
package de.gematik.rbellogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnownUuidsContainerTest {

  private final KnownUuidsContainer container = new KnownUuidsContainer(new Object());

  @Test
  @DisplayName("A uuid can only be claimed once")
  void aUuidCanOnlyBeClaimedOnce() {
    assertThat(container.add("m1")).isTrue();
    assertThat(container.add("m1")).isFalse();
  }

  @Test
  @DisplayName("A claimed uuid is contained, but not yet converted")
  void claimedUuidIsContainedButNotConverted() {
    container.add("m1");

    assertThat(container.contains("m1"))
        .as("waiting parse tasks are released on this, so it has to see a claim in flight")
        .isTrue();
    assertThat(container.isAlreadyConverted("m1")).isFalse();

    container.markAsConverted("m1");
    assertThat(container.isAlreadyConverted("m1")).isTrue();
  }

  @Test
  @DisplayName("Removing a uuid frees it for a new claim")
  void removingAUuidFreesIt() {
    container.add("m1");
    container.remove("m1");

    assertThat(container.contains("m1")).isFalse();
    assertThat(container.add("m1")).isTrue();
  }

  @Test
  @DisplayName("The handler list tolerates being extended while it is notified")
  void handlerListToleratesBeingExtendedWhileNotified() {
    container.addRemovedMessageUuidsHandler(
        uuids -> container.addRemovedMessageUuidsHandler(more -> {}));
    container.add("m1");

    assertThatNoException()
        .as(
            "remove() notifies the handlers outside the monitor, so a registration landing during"
                + " that notification must not break the iteration")
        .isThrownBy(() -> container.remove("m1"));
  }
}
