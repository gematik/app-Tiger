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

package de.gematik.test.tiger.exceptions;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.CUSTOM_FAILURE_MESSAGE;

import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestStepFinished;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.lang3.reflect.FieldUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FailMessageOverrider {

  @SneakyThrows
  public static void overrideFailureMessage(TestStepFinished event) {
    if (Status.FAILED.equals(event.getResult().getStatus())) {
      val customMessage = CUSTOM_FAILURE_MESSAGE.getValueOrDefault();
      if (!customMessage.isBlank()
          && !(event.getResult().getError() instanceof CustomAssertionError)) {
        FieldUtils.writeField(
            event.getResult(),
            "error",
            new CustomAssertionError(customMessage, event.getResult().getError()),
            true);
      }
    }
  }
}
