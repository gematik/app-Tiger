/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
