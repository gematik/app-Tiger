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

import java.util.Map;

/*
 * @author jamesdbloom
 */
public class Cookies extends KeysAndValues<Cookie, Cookies> {

  public Cookies(Cookie... cookies) {
    withEntries(cookies);
  }

  public Cookies(Map<String, String> cookies) {
    super(cookies);
  }

  @Override
  public Cookie build(String name, String value) {
    return new Cookie(name, value);
  }

  public Cookies clone() {
    return new Cookies(getMap());
  }
}
