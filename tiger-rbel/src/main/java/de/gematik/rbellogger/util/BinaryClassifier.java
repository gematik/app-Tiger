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

package de.gematik.rbellogger.util;

public class BinaryClassifier {

    private static final int BYTES_TO_CHECK = 100;

    public static boolean isBinary(byte[] data) {
        for (int pos = 0;
            pos < BYTES_TO_CHECK
                && pos < data.length; pos++) {
            //CR LF
            if (data[pos] == 0xA
                || data[pos] == 0xD) {
                continue;
            }
            if (data[pos] < 0x20) {
                return true;
            }
        }
        return false;
    }
}
