/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.Test;

public class TestOSEnvironment {

    @Test
    public void getEnvAsStringPATHOK() {
        assertThat(OSEnvironment.getAsString(System.getenv().keySet().iterator().next())).isNotBlank();
    }

    @Test
    public void getEnvAsStringNotExistingWithDefaultOK() {
        assertThat(OSEnvironment.getAsString("_______NOT____EXISTS", "DEFAULT")).isEqualTo("DEFAULT");
    }

    @Test
    public void getEnvAsStringExistingNotDefaultOK() {
        assertThat(OSEnvironment.getAsString(System.getenv().keySet().iterator().next(), "_________DEFAULT")).isNotEqualTo("_________DEFAULT");
    }

    @Test
    public void getEnvAsBooleanOK() {
        OSEnvironment.setEnv(Map.of("TEST_TIGER_ENV", "1"));
        assertThat(OSEnvironment.getAsBoolean("TEST_TIGER_ENV")).isTrue();
    }


}
