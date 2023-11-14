/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.util;

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
