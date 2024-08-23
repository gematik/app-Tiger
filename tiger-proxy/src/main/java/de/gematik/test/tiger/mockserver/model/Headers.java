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

import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;

/*
 * @author jamesdbloom
 */
public class Headers extends KeysToMultiValues<Header, Headers> {

  public Headers(List<Header> headers) {
    withEntries(headers);
  }

  public Headers(Header... headers) {
    withEntries(headers);
  }

  public Headers(Multimap<String, String> headers) {
    super(headers);
  }

  public static Headers headers(Header... headers) {
    return new Headers(headers);
  }

  @Override
  public Header build(String name, Collection<String> values) {
    return new Header(name, values);
  }

  protected void isModified() {}

  public Headers withKeyMatchStyle(KeyMatchStyle keyMatchStyle) {
    super.withKeyMatchStyle(keyMatchStyle);
    return this;
  }

  public Headers clone() {
    return new Headers(getMultimap());
  }
}
