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

package de.gematik.test.tiger.common.data.config.tigerProxy;

import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TigerTlsConfiguration {

  private TigerConfigurationPkiIdentity serverRootCa;
  private TigerConfigurationPkiIdentity forwardMutualTlsIdentity;
  private TigerConfigurationPkiIdentity serverIdentity;
  @Builder.Default private String domainName = "localhost";
  @Builder.Default private List<String> alternativeNames = List.of("127.0.0.1", "localhost");
  // localhost will be part of the certificates twice by default. This is done in case someone just
  // sets the url
  // and assumes localhost will still be supported
  private List<String> serverSslSuites;
  private List<String> clientSslSuites;
  private List<String> serverTlsProtocols;
  private List<String> clientSupportedGroups;
}
