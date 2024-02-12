/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.mockserver.matchers;

import de.gematik.test.tiger.mockserver.model.HttpRequest;

/*
 * @author jamesdbloom
 */
public class MatchDifferenceCount {

  private final HttpRequest httpRequest;
  private Integer failures = 0;

  public MatchDifferenceCount(HttpRequest httpRequest) {
    this.httpRequest = httpRequest;
  }

  public HttpRequest getHttpRequest() {
    return httpRequest;
  }

  @SuppressWarnings("UnusedReturnValue")
  public MatchDifferenceCount incrementFailures() {
    this.failures++;
    return this;
  }

  public Integer getFailures() {
    return failures;
  }
}
