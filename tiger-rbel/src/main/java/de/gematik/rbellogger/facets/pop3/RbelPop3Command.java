/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.facets.pop3;

import java.util.Arrays;
import java.util.Collections;

public enum RbelPop3Command {
  CAPA,
  USER,
  PASS,
  QUIT,
  STAT,
  LIST,
  RETR,
  DELE,
  NOOP,
  RSET,
  APOP,
  TOP,
  UIDL,
  AUTH,
  SASL;

  public static final int MAX_LENGTH =
      Collections.max(
          Arrays.stream(values()).map(RbelPop3Command::name).map(String::length).toList());

  public static RbelPop3Command fromStringIgnoringCase(String s) {
    return RbelPop3Command.valueOf(s.toUpperCase());
  }
}
