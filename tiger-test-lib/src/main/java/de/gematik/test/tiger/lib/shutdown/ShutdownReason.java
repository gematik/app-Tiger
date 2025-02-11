/*
 * Copyright 2025 gematik GmbH
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
 */

package de.gematik.test.tiger.lib.shutdown;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
public class ShutdownReason {
  @Getter private final String message;
  @Getter private boolean shouldUserAcknowledgeShutdown = true;
  private Exception exception;

  public Optional<Exception> getException() {
    return Optional.ofNullable(exception);
  }

  public static final ShutdownReason REGULAR_SHUTDOWN_USER_ACKNOWLEDGE =
      new ShutdownReason("Test run finished, press SHUTDOWN");

  public static final ShutdownReason REGULAR_SHUTDOWN_NO_ACKNOWLEDGE =
      new ShutdownReason("Test run finished, press SHUTDOWN", false, null);
}
