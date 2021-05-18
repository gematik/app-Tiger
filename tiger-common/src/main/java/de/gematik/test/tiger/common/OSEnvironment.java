/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;

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
            var theEnvironmentField = FieldUtils.getField(processEnvironmentClass, "theEnvironment", true);
            theEnvironmentField.setAccessible(true);
            final var theCaseInsensitiveEnvironmentField
                = FieldUtils.getField(processEnvironmentClass, "theCaseInsensitiveEnvironment", true);
            if (theCaseInsensitiveEnvironmentField != null) {
                // Windows
                theCaseInsensitiveEnvironmentField.setAccessible(true);
                final Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
                env.putAll(newenv);
                final Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
                cienv.putAll(newenv);
            } else {
                newenv.entrySet().stream()
                    .forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

                /*                final var theUnmodifiableEnvironment
                    = FieldUtils.getField(processEnvironmentClass, "theUnmodifiableEnvironment");
//                findInnerClass(processEnvironmentClass, "Variable")
//                    .getDeclaredMethod("valueOf", String.class)
//                    .setAccessible(true);
                final HashMap<Object, Object> theEnvironment
                    = (HashMap<Object, Object>) theEnvironmentField.get(null);
//                ClassUtils.
//                newenv.entrySet().stream()
//                    .forEach(entry -> theEnvironment.put(Variable.valueOf(entry.getKey()),
//                        Value.valueOf(entry.getValue()));
//
//                theUnmodifiableEnvironment = Collections.unmodifiableMap
//                    (new StringEnvironment(theEnvironment));
                */
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Class findInnerClass(Class outerClass, String name) {
        return Stream.of(outerClass.getDeclaredClasses())
            .filter(clz -> clz.getSimpleName().equals(name))
            .findAny()
            .orElse(null);
    }
}
