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

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/*
 * @author jamesdbloom
 */
public abstract class RequestDefinition extends ObjectWithJsonToString {

  private String logCorrelationId;

  @JsonIgnore
  public String getLogCorrelationId() {
    return logCorrelationId;
  }

  public RequestDefinition withLogCorrelationId(String logCorrelationId) {
    this.logCorrelationId = logCorrelationId;
    return this;
  }

  public abstract RequestDefinition shallowClone();

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
