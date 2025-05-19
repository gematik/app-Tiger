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

import static de.gematik.rbellogger.writer.RbelContentTreeConverter.ENCODE_AS;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.facets.jose.RbelJwtFacet;
import de.gematik.rbellogger.facets.jose.RbelJwtSignature;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;

public class RbelJwtElementToNodeConverter implements RbelElementToContentTreeNodeConverter {

  @Override
  public boolean shouldConvert(RbelElement target) {
    return target.hasFacet(RbelJwtFacet.class);
  }

  @Override
  public RbelContentTreeNode convert(
      RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
    final RbelMultiMap<RbelContentTreeNode> map =
        new RbelMultiMap<RbelContentTreeNode>()
            .with(
                "header",
                converter
                    .convertNode(
                        el.getFacetOrFail(RbelJwtFacet.class).getHeader(), "header", context)
                    .get(0))
            .with(
                "body",
                converter
                    .convertNode(el.getFacetOrFail(RbelJwtFacet.class).getBody(), "body", context)
                    .get(0))
            .with(
                "signature",
                convertSignature(
                    el.getFacetOrFail(RbelJwtFacet.class).getSignature(), context, converter));
    final RbelStrictOrderContentTreeNode result =
        new RbelStrictOrderContentTreeNode(map, el.getRawContent());
    result.setType(
        context
            .readStringOptional(ENCODE_AS)
            .map(RbelContentType::seekValueFor)
            .orElse(RbelContentType.JWT));
    return result;
  }

  private RbelContentTreeNode convertSignature(
      RbelElement signature, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
    final RbelMultiMap<RbelContentTreeNode> content = new RbelMultiMap<>();
    if (signature.getFacetOrFail(RbelJwtSignature.class).getVerifiedUsing() != null) {
      content.with(
          "verifiedUsing",
          RbelElementWrapperContentTreeNode.constructFromValueElement(
              signature.getFacetOrFail(RbelJwtSignature.class).getVerifiedUsing(),
              context,
              converter.getJexlContext()));
    }
    final RbelStrictOrderContentTreeNode result =
        new RbelStrictOrderContentTreeNode(content, signature.getRawContent());
    result.setKey("signature");
    return result;
  }
}
