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
