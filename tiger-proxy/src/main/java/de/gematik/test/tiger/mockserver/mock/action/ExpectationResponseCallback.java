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

package de.gematik.test.tiger.mockserver.mock.action;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;

/*
 * @author jamesdbloom
 */
public interface ExpectationResponseCallback extends ExpectationCallback<HttpResponse> {

  /**
   * Called for every request when expectation condition has been satisfied. The request that
   * satisfied the expectation condition is passed as the parameter and the return value is the
   * request that will be returned.
   *
   * @param httpRequest the request that satisfied the expectation condition
   * @return the response that will be returned
   */
  HttpResponse handle(HttpRequest httpRequest) throws Exception;
}
