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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
@JsonInclude(Include.NON_NULL)
public class TigerRoute implements Serializable {

  @With private String id;
  private String from;
  private String to;
  @Builder.Default private boolean internalRoute = false;
  private boolean disableRbelLogging;
  private TigerBasicAuthConfiguration basicAuth;
  private List<String> criterions;
  @Builder.Default private List<String> hosts = new ArrayList<>();

  public String createShortDescription() {
    final StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("{from='");
    resultBuilder.append(from);
    resultBuilder.append('\'');
    resultBuilder.append(", to='");
    resultBuilder.append(to);
    resultBuilder.append('\'');
    if (criterions != null && !criterions.isEmpty()) {
      resultBuilder.append(", criterions=");
      resultBuilder.append(criterions);
    }
    if (hosts != null && !hosts.isEmpty()) {
      resultBuilder.append(", hosts=");
      resultBuilder.append(hosts);
    }
    resultBuilder.append('}');
    return resultBuilder.toString();
  }
}
