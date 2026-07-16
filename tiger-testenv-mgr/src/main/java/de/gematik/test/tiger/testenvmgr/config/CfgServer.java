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
package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.common.data.config.CfgExternalJarOptions;
import de.gematik.test.tiger.common.data.config.CfgHelmChartOptions;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.JsonNode;

@Data
@Accessors(fluent = false, chain = true)
public class CfgServer {

  private String hostname;

  private String type;

  /**
   * References another server which has to be booted prior to this. Multiple servers can be
   * referenced, divided by comma.
   */
  private String dependsUpon;

  /**
   * Idempotently appends a server id to {@link #dependsUpon} (comma-separated). No-op when {@code
   * id} is blank or already present. Trims existing entries before comparison; preserves order and
   * inserts the new id at the end. Used by pre-validation hooks (see {@code
   * AbstractTigerServer#prepareDependencies()}) to inject implicit boot-order edges before the
   * dependency graph is built.
   */
  public CfgServer addDependsUpon(String id) {
    if (id == null || id.isBlank()) {
      return this;
    }
    String trimmedId = id.trim();
    if (dependsUpon == null || dependsUpon.isBlank()) {
      dependsUpon = trimmedId;
      return this;
    }
    java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
    for (String part : dependsUpon.split(",")) {
      String t = part.trim();
      if (!t.isEmpty()) {
        tokens.add(t);
      }
    }
    if (tokens.add(trimmedId)) {
      dependsUpon = String.join(",", tokens);
    }
    return this;
  }

  private List<String> source = new ArrayList<>();
  private String version;
  private Integer startupTimeoutSec;
  private Integer startupPollIntervalMs;
  private boolean active = true;
  private String healthcheckUrl;
  private Integer healthcheckReturnCode;
  private String logFile;

  private CfgExternalJarOptions externalJarOptions;
  private TigerProxyConfiguration tigerProxyConfiguration;
  private CfgHelmChartOptions helmChartOptions = new CfgHelmChartOptions();

  /**
   * Out-of-tree, server-type-specific configuration blocks, keyed by an arbitrary string token
   * (typically the {@code @TigerServerType} value of the consuming server). Read by {@code
   * AbstractTigerServer#readTypeSpecificConfig(String, Class)}.
   *
   * <p>This is the extension hook that lets server types defined in separate (downstream) artifacts
   * attach typed configuration to a {@code CfgServer} entry without forcing core to import their
   * classes — see {@code doc/adr/canopy-extension-repo-extraction.md}.
   *
   * <p>Surfaced in YAML as <em>bare top-level keys</em> on the server entry (matching the {@code
   * tigerProxyConfiguration} convention), thanks to the {@link JsonAnyGetter} / {@link
   * JsonAnySetter} pair below. So users write:
   *
   * <pre>{@code
   * servers:
   *   myCanopy:
   *     type: canopy
   *     canopy:           # <-- bare key, lands in typeSpecificConfig['canopy']
   *       dnsPort: 5353
   * }</pre>
   *
   * <p>In-tree server types should prefer the {@link
   * de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer#getConfigurationBeanClass()}
   * mechanism (a typed {@code CfgServer} subclass) where it is more convenient.
   */
  @JsonIgnore private final Map<String, JsonNode> typeSpecificConfig = new LinkedHashMap<>();

  /**
   * Jackson serialization hook: emits each {@link #typeSpecificConfig} entry as a top-level
   * property of this {@code CfgServer}. Round-trips with {@link #setTypeSpecificConfigEntry}.
   */
  @JsonAnyGetter
  public Map<String, JsonNode> getTypeSpecificConfig() {
    return typeSpecificConfig;
  }

  /**
   * Jackson deserialization hook: any YAML/JSON property on this {@code CfgServer} that does not
   * match a typed field (above) is collected here. The trade-off is that <em>unrecognised</em> keys
   * (typos in known fields, abandoned config) silently land in this bag instead of failing
   * Jackson's parse. {@code TigerTestEnvMgr} validates after startup that every key in the bag was
   * claimed by some server's {@code readTypeSpecificConfig} call (see {@link
   * #getUnreadTypeSpecificConfigKeys}).
   */
  @JsonAnySetter
  public void setTypeSpecificConfigEntry(String key, JsonNode value) {
    typeSpecificConfig.put(key, value);
  }

  /**
   * Tracks which {@link #typeSpecificConfig} keys have been read by an {@code
   * AbstractTigerServer#readTypeSpecificConfig} call. Used to surface orphan/typo keys after
   * startup.
   */
  @JsonIgnore
  private final java.util.Set<String> readTypeSpecificConfigKeys = new java.util.LinkedHashSet<>();

  /** Internal: called by {@code AbstractTigerServer#readTypeSpecificConfig}. */
  @JsonIgnore
  public void markTypeSpecificConfigKeyRead(String key) {
    readTypeSpecificConfigKeys.add(key);
  }

  /**
   * Returns the keys present in {@link #typeSpecificConfig} that no server has read so far. Use
   * after startup to warn about typos.
   */
  @JsonIgnore
  public java.util.Set<String> getUnreadTypeSpecificConfigKeys() {
    var unread = new java.util.LinkedHashSet<>(typeSpecificConfig.keySet());
    unread.removeAll(readTypeSpecificConfigKeys);
    return unread;
  }

  /** list of env vars to be set for docker, external Jar/TigerProxy */
  private List<String> environment = new ArrayList<>();

  /** mappings for local tiger proxy to be set when this server is active */
  private final List<String> urlMappings = new ArrayList<>();

  /**
   * properties to be exported to subsequent nodes as env vars and set as system properties to
   * current jvm
   */
  private final List<String> exports = new ArrayList<>();

  private int uiRank = -1;
}
