/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
