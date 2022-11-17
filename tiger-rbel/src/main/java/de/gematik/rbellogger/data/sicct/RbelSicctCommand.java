/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.data.sicct;

import de.gematik.rbellogger.data.RbelElement;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.stream.Stream;

@RequiredArgsConstructor
public enum RbelSicctCommand {
    // compare SICCT-specification, chapter 5.5.7
    SICCT_RESET((byte) 0x80, (byte) 0x11),
    SICCT_REQUEST((byte) 0x80, (byte) 0x12),
    SICCT_GET_STATUS((byte) 0x80, (byte) 0x13),
    SICCT_EJECT((byte) 0x80, (byte) 0x15),
    SICCT_INPUT((byte) 0x80, (byte) 0x16),
    SICCT_OUTPUT((byte) 0x80, (byte) 0x17),
    SICCT_PERFORM_VERIFICATION((byte) 0x80, (byte) 0x18),
    SICCT_MODIFY_VERIFICATION_DATA((byte) 0x80, (byte) 0x19),
    SICCT_SELECT_CT_MODE((byte) 0x80, (byte) 0x20),
    SICCT_COMFORT_AUTHENTICATION((byte) 0x80, (byte) 0x21),
    SICCT_COMFORT_ENROLL((byte) 0x80, (byte) 0x22),
    SICCT_SET_STATUS((byte) 0x8, (byte) 0x23),
    SICCT_DOWNLOAD_INIT((byte)0x80, (byte)0x24),
    SICCT_DOWNLOAD_DATA((byte)0x80, (byte)0x25),
    SICCT_DOWNLOAD_FINISH((byte)0x80, (byte)0x26),
    SICCT_CONTROLCOMMAND((byte)0x80, (byte)0x27),
    SICCT_INIT_CT_SESSION((byte)0x80, (byte)0x28),
    SICCT_CLOSE_CT_SESSION((byte)0x80, (byte)0x29);

    final byte cla;
    final byte ins;

    public static Optional<RbelSicctCommand> from(RbelElement cla, RbelElement ins) {
        return Stream.of(values())
            .filter(cmd -> cla.getRawContent() != null && cla.getRawContent().length == 1)
            .filter(cmd -> cla.getRawContent()[0] == cmd.cla)
            .filter(cmd -> ins.getRawContent() != null && ins.getRawContent().length == 1)
            .filter(cmd -> ins.getRawContent()[0] == cmd.ins)
            .findAny();
    }
}
