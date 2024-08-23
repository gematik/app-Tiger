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

package de.gematik.rbellogger.writer.tree;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.util.List;
import java.util.Optional;
import lombok.val;

public class RbelElementWrapperContentTreeNode extends RbelContentTreeNode {

  public static RbelElementWrapperContentTreeNode constructFromRbelElement(
      RbelElement source,
      TigerConfigurationLoader conversionContext,
      TigerJexlContext jexlContext) {
    var result = new RbelElementWrapperContentTreeNode();
    result.setContent(tryToResolvePlaceholdersIfPresent(source, conversionContext, jexlContext));
    return result;
  }

  private static byte[] tryToResolvePlaceholdersIfPresent(
      RbelElement source,
      TigerConfigurationLoader conversionContext,
      TigerJexlContext jexlContext) {
    val originalString = source.getRawStringContent();
    val substitutedString =
        TokenSubstituteHelper.substitute(
            originalString, conversionContext, Optional.ofNullable(jexlContext));
    if (originalString.equals(substitutedString)) {
      return source.getRawContent();
    } else {
      return substitutedString.getBytes(source.getElementCharset());
    }
  }

  public static RbelElementWrapperContentTreeNode constructFromValueElement(
      RbelElement source,
      TigerConfigurationLoader conversionContext,
      TigerJexlContext jexlContext) {
    var result = new RbelElementWrapperContentTreeNode();
    result.setContent(
        TokenSubstituteHelper.substitute(
                source.printValue().orElseGet(source::getRawStringContent),
                conversionContext,
                Optional.ofNullable(jexlContext))
            .getBytes());
    return result;
  }

  private RbelElementWrapperContentTreeNode() {
    super(new RbelMultiMap<>(), null);
  }

  @Override
  public List<RbelContentTreeNode> getChildNodes() {
    return List.of();
  }

  @Override
  public String toString() {
    return "wrappernode:{<node child nodes>, content=" + new String(getContent()) + "}";
  }
}
