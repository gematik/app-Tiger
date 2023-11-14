/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Data
public class TigerServerStatusUpdateDto {

  private String statusMessage;
  private String type;
  private String baseUrl;
  private TigerServerStatus status;

  public static TigerServerStatusUpdateDto fromUpdate(final TigerServerStatusUpdate value) {
    return TigerServerStatusUpdateDto.builder()
        .statusMessage(value.getStatusMessage())
        .type(value.getType())
        .status(value.getStatus())
        .baseUrl(value.getBaseUrl())
        .build();
  }
}
