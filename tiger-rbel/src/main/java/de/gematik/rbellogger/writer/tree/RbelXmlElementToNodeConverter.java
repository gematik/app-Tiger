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

package de.gematik.rbellogger.writer.tree;

import static de.gematik.rbellogger.writer.RbelContentTreeConverter.ENCODE_AS;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelXmlAttributeFacet;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class RbelXmlElementToNodeConverter implements RbelElementToContentTreeNodeConverter {

  public static final String IS_XML_ATTRIBUTE = "isXmlAttribute";

  @Override
  public boolean shouldConvert(RbelElement target) {
    return target.hasFacet(RbelXmlFacet.class);
  }

  @Override
  public RbelContentTreeNode convert(
      RbelElement el, TigerConfigurationLoader context, RbelContentTreeConverter converter) {
    final RbelMultiMap<RbelContentTreeNode> map =
        el.getChildNodesWithKey().stream()
            .flatMap(
                entry ->
                    convertNode(context, converter, entry).stream()
                        .map(childNode -> Pair.of(entry.getKey(), childNode)))
            .collect(RbelMultiMap.COLLECTOR);
    final RbelStrictOrderContentTreeNode result =
        new RbelStrictOrderContentTreeNode(map, el.getRawContent());
    result.setType(
        context
            .readStringOptional(ENCODE_AS)
            .map(RbelContentType::seekValueFor)
            .orElse(RbelContentType.XML));
    return result;
  }

  private List<RbelContentTreeNode> convertNode(
      TigerConfigurationLoader context,
      RbelContentTreeConverter converter,
      Entry<String, RbelElement> entry) {
    final List<RbelContentTreeNode> nodes = new ArrayList<>();
    for (RbelContentTreeNode node :
        converter.convertNode(entry.getValue(), entry.getKey(), context)) {
      // add attributes
      if (entry.getValue().hasFacet(RbelXmlAttributeFacet.class)) {
        node.attributes().put(IS_XML_ATTRIBUTE, "true");
      }

      // manage pulling up/down of text-nodes in mode-switches
      if (entry.getValue().hasFacet(RbelXmlFacet.class)) {
        final List<RbelContentTreeNode> childNodes = new ArrayList<>();
        for (RbelContentTreeNode childNode : node.getChildNodes()) {
          addChildNode(node, childNode, childNodes);
        }
        node.setupChildNodes(childNodes);
      }
      nodes.add(node);
    }
    return nodes;
  }

  private static void addChildNode(
      RbelContentTreeNode node,
      RbelContentTreeNode childNode,
      List<RbelContentTreeNode> childNodes) {
    if (childNode.getType() == null
        && childNode.getKey().orElseThrow().equals("text")
        && node.getType() != RbelContentType.XML) {
      node.setContent(childNode.getContent());
      node.setupChildNodes(List.of());
      log.trace("pulling up node '{}'", node.getRawStringContent());
    } else if (!childNode.hasTypeOptional(RbelContentType.XML).orElse(true)
        && !childNode.getKey().orElseThrow().equals("text")) {
      // wrap in text-node (will be rendered as text inside the xml)
      RbelContentTreeNode wrapperNode =
          new RbelStrictOrderContentTreeNode(
              new RbelMultiMap<RbelContentTreeNode>()
                  .with(childNode.getKey().orElseThrow(), childNode),
              null);
      wrapperNode.setType(childNode.getType());
      wrapperNode.setKey("text");
      wrapperNode.setCharset(node.getElementCharset());
      childNodes.add(wrapperNode);
      log.trace("wrapping node {}", node.getContent());
    } else {
      childNodes.add(childNode);
    }
  }
}
