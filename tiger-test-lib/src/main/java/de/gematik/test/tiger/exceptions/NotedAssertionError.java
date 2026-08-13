/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.exceptions;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotedAssertionError {
  public static AssertionError createNotedAssertionError(
      @NonNull RbelElement targetOfNote, @NonNull String assertionErrorMessage) {
    return createNotedException(
        targetOfNote,
        "Assertion Error: " + assertionErrorMessage,
        () -> new AssertionError(assertionErrorMessage));
  }

  public static <E extends Throwable> E createNotedException(
      @NonNull RbelElement target,
      @NonNull String noteMessage,
      @NonNull Supplier<E> exceptionSupplier) {
    target.addFacet(RbelNoteFacet.error(noteMessage));
    return exceptionSupplier.get();
  }
}
