/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.data;

import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogUpdate;
import java.time.LocalDateTime;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@EqualsAndHashCode
public class TigerServerLogDto {
  private String serverName;
  private String logLevel;
  private LocalDateTime localDateTime;
  private String logMessage;

  public static TigerServerLogDto createFrom(final TigerServerLogUpdate update) {
    return TigerServerLogDto.builder()
        .logLevel(update.getLogLevel())
        .logMessage(update.getLogMessage())
        .serverName(update.getServerName())
        .localDateTime(LocalDateTime.now())
        .build();
  }
}
