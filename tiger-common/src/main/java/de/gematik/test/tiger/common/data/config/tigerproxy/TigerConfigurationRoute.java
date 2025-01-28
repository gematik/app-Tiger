/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.common.data.config.tigerproxy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@JsonInclude(Include.NON_NULL)
public class TigerConfigurationRoute {

  private String from;

  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  @Singular("to")
  private List<String> to = new ArrayList<>();

  private boolean disableRbelLogging;
  private TigerRouteAuthenticationConfiguration authentication;
  private List<String> criterions;
  @Builder.Default private List<String> hosts = new ArrayList<>();
  /**
   * Should the route be matched for both forward- and reverse-proxy-requests?
   * If false only requests matching the proxy-mode suggested by the "from" attribute
   * will be handled.
   */
  @Builder.Default private boolean matchForProxyType = true;
}
