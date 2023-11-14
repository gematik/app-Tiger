/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import java.util.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@NoArgsConstructor
public class Configuration {

  private final Map<String, CfgServer> servers = new HashMap<>();
  private boolean localProxyActive = true;
  private TigerProxyConfiguration tigerProxy;

  @Builder
  @SuppressWarnings("unused")
  private Configuration(boolean localProxyActive) {
    this.localProxyActive = Optional.of(localProxyActive).orElse(true);
  }
}
