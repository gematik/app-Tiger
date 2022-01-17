/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.Test;

public class TestOSEnvironment {

    @Test
    public void getEnvAsStringPATHOK() {
        assertThat(TigerGlobalConfiguration.readString(System.getenv().keySet().iterator().next())).isNotBlank();
    }

    @Test
    public void getEnvAsStringNotExistingWithDefaultOK() {
        assertThat(TigerGlobalConfiguration.readString("_______NOT____EXISTS", "DEFAULT")).isEqualTo("DEFAULT");
    }

    @Test
    public void getEnvAsStringExistingNotDefaultOK() {
        assertThat(TigerGlobalConfiguration.readString(System.getenv().keySet().iterator().next(), "_________DEFAULT")).isNotEqualTo("_________DEFAULT");
    }
}
