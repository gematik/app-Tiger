/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.data;

import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class TigerServerStatusDto {

  private String name;
  private String baseUrl;
  private String type;
  private TigerServerStatus status;
  private String statusMessage;
  @Builder.Default private List<String> statusUpdates = new ArrayList<>();
}
