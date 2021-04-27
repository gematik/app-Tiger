package de.gematik.test.tiger.common;

import java.util.Optional;

public class OSEnvironment {

    public static String getAsString(String name) {
        return Optional.ofNullable(System.getenv(name)).orElse(System.getProperty(name));
    }

    public static String getAsString(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name)).orElse(System.getProperty(name, defaultValue));
    }

    public static boolean getAsBoolean(String name) {
        String val = getAsString(name);
        return val == null ? false : val.equals("1");
    }
}
