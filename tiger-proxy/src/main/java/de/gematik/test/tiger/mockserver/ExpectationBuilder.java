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

package de.gematik.test.tiger.mockserver;

import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.netty.MockServer;
import lombok.AllArgsConstructor;

/*
 * @author jamesdbloom
 */
@AllArgsConstructor
public class ExpectationBuilder {

  private final Expectation expectation;
  private final MockServer mockServer;

  public Expectation[] forward(
      final ExpectationForwardAndResponseCallback expectationForwardAndResponseCallback) {
    expectation.thenForward(expectationForwardAndResponseCallback);
    mockServer.getHttpState().add(expectation);
    return new Expectation[] {expectation};
  }

  public ExpectationBuilder id(String id) {
    if (id != null) {
      expectation.setId(id);
    }
    return this;
  }
}
