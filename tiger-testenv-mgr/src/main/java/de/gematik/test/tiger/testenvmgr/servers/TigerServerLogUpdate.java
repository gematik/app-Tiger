package de.gematik.test.tiger.testenvmgr.servers;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerServerLogUpdate {
  private String serverName;
  private String logLevel;
  private String logMessage;
}
