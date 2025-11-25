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
package de.gematik.test.tiger.mockserver.model;

import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

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

  public Stream<String> streamHeaderValuesForField(String headerName) {
    return getEntries().stream()
        .filter(h -> h.getName().equalsIgnoreCase(headerName))
        .map(Header::getValues)
        .flatMap(Collection::stream);
  }
}
