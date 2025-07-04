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
package de.gematik.rbellogger.facets.xml;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public class MtomPart {
  private final String messageContent;
  private final Map<String, String> messageHeader;

  public MtomPart(String message) {
    final String[] messageParts = message.split("(\r\n\r\n|\n\n)", 2);
    if (messageParts.length == 2) {
      messageContent = messageParts[1];
      messageHeader =
          Stream.of(messageParts[0].trim().split("(\r\n|\n)"))
              .map(s -> s.split(": ", 2))
              .filter(ar -> ar.length == 2)
              .collect(Collectors.toMap(ar -> ar[0], ar -> ar[1]));
    } else {
      messageContent = null;
      messageHeader = Map.of();
    }
  }
}
