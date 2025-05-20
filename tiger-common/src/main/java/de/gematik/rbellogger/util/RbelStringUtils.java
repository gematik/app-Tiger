/*
 *
 * Copyright 2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gematik.rbellogger.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelStringUtils {
  public static String bytesToStringWithoutNonPrintableCharacters(byte[] content, int maxLength) {
    return StringUtils.abbreviate(bytesToStringWithoutNonPrintableCharacters(content), maxLength);
  }

  public static String bytesToStringWithoutNonPrintableCharacters(byte[] content) {
    return new String(content)
        .replace("\r\n", "<CRLF>")
        .replace("\n", "<LF>")
        .replace("\r", "<CR>");
  }
}
