/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import org.junit.jupiter.api.Test;

class TigerExceptionUtilsTest {

    @Test
    public void standardCase() {
        assertThat(TigerExceptionUtils.getCauseWithType(new RuntimeException("blub", new SocketException("root cause message")), SocketException.class))
            .get()
            .hasSameClassAs(new SocketException())
            .matches(e -> e.getMessage().equals("root cause message"));
    }

    @Test
    public void flatExceptionCase() {
        assertThat(TigerExceptionUtils.getCauseWithType(new SocketException("flat cause message"), SocketException.class))
            .get()
            .hasSameClassAs(new SocketException())
            .matches(e -> e.getMessage().equals("flat cause message"));
    }

    @Test
    public void couldNotMatchException_rootCausePresent() {
        assertThat(TigerExceptionUtils.getCauseWithType(new RuntimeException("blub", new SocketException("root cause message")), FileNotFoundException.class))
            .isEmpty();
    }

    @Test
    public void couldNotMatchException_noRootCausePresent() {
        assertThat(TigerExceptionUtils.getCauseWithType(new RuntimeException("blub"), IOException.class))
            .isEmpty();
    }

    @Test
    public void nullCase() {
        assertThat(TigerExceptionUtils.getCauseWithType(null, IOException.class))
            .isEmpty();
    }
}