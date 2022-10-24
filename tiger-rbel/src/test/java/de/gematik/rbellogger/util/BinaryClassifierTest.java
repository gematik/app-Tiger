/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class BinaryClassifierTest {

    @Test
    public void someTextExamples() {
        assertThat(BinaryClassifier.isBinary("hello world".getBytes()))
            .isFalse();
        assertThat(BinaryClassifier.isBinary("hello world\n".getBytes()))
            .isFalse();
    }

    @Test
    public void someBinaryExamples() {
        assertThat(BinaryClassifier.isBinary(new byte[]{0x00, 0x01}))
            .isTrue();
        assertThat(BinaryClassifier.isBinary(new byte[]{0x11, 0x7F}))
            .isTrue();
    }
}