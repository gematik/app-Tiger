/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OSEnvironment {

    public static String getAsString(String name) {
        return Optional.ofNullable(System.getenv(name)).orElse(System.getProperty(name));
    }

    public static String getAsString(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name)).orElse(System.getProperty(name, defaultValue));
    }

    public static boolean getAsBoolean(String name) {
        var val = getAsString(name);
        return val != null && val.equals("1");
    }
}
