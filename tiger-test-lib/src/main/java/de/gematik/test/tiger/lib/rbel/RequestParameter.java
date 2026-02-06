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
package de.gematik.test.tiger.lib.rbel;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

@Builder
@Getter
@ToString
public class RequestParameter {

  private String path;
  private String rbelPath;
  private String value;
  @Setter private String host;
  @Setter private Integer port;
  @Setter private String method;
  private final boolean startFromPreviouslyFoundMessage;
  private final boolean filterPreviousRequest;
  private final boolean requireNewMessage;
  @Builder.Default private final boolean requireRequestMessage = true;
  @Builder.Default private final boolean sameConnection = false;

  public RequestParameter resolvePlaceholders() {
    if (StringUtils.isNotEmpty(path)) {
      path = TigerGlobalConfiguration.resolvePlaceholders(path);
    }
    if (StringUtils.isNotEmpty(rbelPath)) {
      rbelPath = TigerGlobalConfiguration.resolvePlaceholders(rbelPath);
    }
    if (StringUtils.isNotEmpty(value)) {
      value = TigerGlobalConfiguration.resolvePlaceholders(value);
    }
    return this;
  }
}
