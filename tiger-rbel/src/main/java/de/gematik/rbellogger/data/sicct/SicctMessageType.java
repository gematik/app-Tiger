/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.sicct;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;

import java.util.stream.Stream;

@RequiredArgsConstructor
public enum SicctMessageType {
    // compare SICCT-specification, chapter 6.1.4.2

    C_COMMAND((byte) 0x6B),
    R_COMMAND((byte) 0x83),
    EVENT_MESSAGE((byte) 0x50);

    final byte value;

    public static SicctMessageType of(byte input) {
        return Stream.of(SicctMessageType.values())
            .filter(type -> type.value == input)
            .findFirst()
            .orElseThrow(() -> new UnknownSicctMessageTypeException(input));
    }

    private static class UnknownSicctMessageTypeException extends RuntimeException {
        public UnknownSicctMessageTypeException(byte input) {
            super("Could not determine message type for " + input + "!");
        }
    }
}
