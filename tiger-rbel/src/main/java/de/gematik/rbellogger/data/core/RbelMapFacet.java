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
package de.gematik.rbellogger.data.core;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.function.Function;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class RbelMapFacet implements RbelFacet {

  private final RbelMultiMap<RbelElement> childNodes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return childNodes;
  }

  public static RbelElement wrap(
      final RbelElement parent,
      final Function<RbelElement, RbelMultiMap<RbelElement>> childNodeFactory,
      final byte[] content) {
    final RbelElement result = new RbelElement(content, parent);
    result.addFacet(new RbelMapFacet(childNodeFactory.apply(result)));
    return result;
  }
}
