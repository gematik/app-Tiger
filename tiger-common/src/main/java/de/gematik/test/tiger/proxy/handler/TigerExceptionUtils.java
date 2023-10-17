/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.handler;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TigerExceptionUtils {

    /**
     * Looks for a cause with the given type and returns it if present. If not then an empty Optional is returned.
     *
     * @param exception The exception to be inspected
     * @param causeClassToDetect The cause-class to detect
     * @param <E> Type of the cause class
     */
    public static <E extends Throwable> Optional<E> getCauseWithType(
        Throwable exception, Class<E> causeClassToDetect) {
        if (causeClassToDetect.isInstance(exception)) {
            return Optional.of((E) exception);
        } else if (exception == null || exception.getCause() == null) {
            return Optional.empty();
        } else {
            return getCauseWithType(exception.getCause(), causeClassToDetect);
        }
    }
}
