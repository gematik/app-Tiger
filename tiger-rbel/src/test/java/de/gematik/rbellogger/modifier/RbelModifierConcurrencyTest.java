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
package de.gematik.rbellogger.modifier;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.RbelModificationDescription;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RbelModifierConcurrencyTest extends AbstractModifierTest {

  private static final int ROUNDS = 3_000;

  @Test
  @DisplayName("Modifications may be added over the REST API while a message is being modified")
  @SneakyThrows
  void modificationsMayBeChangedWhileAMessageIsModified() throws IOException {
    var message = readAndConvertCurlMessage("src/test/resources/sampleMessages/getRequest.curl");
    for (int i = 0; i < 20; i++) {
      addModification("standing-" + i);
    }

    var failure = new AtomicReference<Throwable>();
    var restApi =
        new Thread(
            () -> {
              for (int i = 0; i < ROUNDS; i++) {
                addModification("transient-" + i);
                rbelLogger.getRbelModifier().deleteModification("transient-" + i);
              }
            });
    restApi.setUncaughtExceptionHandler((thread, thrown) -> failure.compareAndSet(null, thrown));

    restApi.start();
    for (int i = 0; i < ROUNDS && failure.get() == null; i++) {
      try {
        modifyMessageAndParseResponse(message);
      } catch (RuntimeException e) {
        failure.compareAndSet(null, e);
      }
    }
    restApi.join();

    assertThat(failure.get())
        .as("applyModifications iterates the map that the REST API mutates from another thread")
        .isNull();
  }

  private void addModification(String name) {
    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .name(name)
                .targetElement("$.header.Version")
                .replaceWith("foobar")
                .build());
  }
}
