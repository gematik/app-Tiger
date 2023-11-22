/*
 * Copyright (c) 2023 gematik GmbH
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
