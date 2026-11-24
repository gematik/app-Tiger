/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.proxy.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * What a Tiger proxy offers to the proxies that are connected to it, see ADR 026. A proxy that does
 * not answer the question offers nothing but the legacy destinations.
 */
@Getter
public class TracingCapabilities {

  /** The destination of the data in binary frames, null if there is none. */
  private final String binaryDestination;

  @JsonCreator
  public TracingCapabilities(@JsonProperty("binaryDestination") String binaryDestination) {
    this.binaryDestination = binaryDestination;
  }
}
