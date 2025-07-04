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
package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.uri.RbelUriFacet;
import java.util.StringJoiner;

public class RbelUriWriter implements RbelElementWriter {

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelUriFacet.class);
  }

  @Override
  public byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    final RbelUriFacet uriFacet = oldTargetElement.getFacetOrFail(RbelUriFacet.class);

    StringBuilder resultBuilder = new StringBuilder();
    if (uriFacet.getBasicPath() == oldTargetModifiedChild) {
      resultBuilder.append(newContent);
    } else {
      resultBuilder.append(uriFacet.getBasicPathString());
    }
    if (!uriFacet.getQueryParameters().isEmpty()) {
      StringJoiner joiner = new StringJoiner("&");
      for (RbelElement queryParameter : uriFacet.getQueryParameters()) {
        if (queryParameter == oldTargetModifiedChild) {
          joiner.add(new String(newContent, oldTargetElement.getElementCharset()));
        } else {
          joiner.add(queryParameter.getRawStringContent());
        }
      }
      resultBuilder.append("?");
      resultBuilder.append(joiner);
    }
    return resultBuilder.toString().getBytes(oldTargetElement.getElementCharset());
  }
}
