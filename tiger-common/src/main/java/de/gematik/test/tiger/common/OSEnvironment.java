package de.gematik.test.tiger.common;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
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

    public static void setEnv(final Map<String, String> newenv) {
        try {
            final Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            var theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            final Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            final var theCaseInsensitiveEnvironmentField = processEnvironmentClass
                .getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            final Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (final NoSuchFieldException | ClassNotFoundException | IllegalAccessException e) {
            final Class<?>[] classes = Collections.class.getDeclaredClasses();
            final Map<String, String> env = System.getenv();
            for (final Class<?> cl : classes) {
                if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    final Field field;
                    try {
                        field = cl.getDeclaredField("m");
                        field.setAccessible(true);
                        final Object obj = field.get(env);
                        final Map<String, String> map = (Map<String, String>) obj;
                        //TODO write unit tests for platforms where this part of code is triggered
                        // and check whether it adds the env vars or overwrites
                        // (as I would assume and which would be a bug)
                        map.clear();
                        map.putAll(newenv);
                    } catch (NoSuchFieldException | IllegalAccessException exc) {
                        log.error("Failed to set environment variables!", exc);
                    }
                }
            }
        }
    }
}
