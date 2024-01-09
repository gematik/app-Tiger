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

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.rbellogger.writer.tree.RbelJsonElementToNodeConverter;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelJsonSerializer implements RbelSerializer {

  @Override
  public byte[] render(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    return renderToString(node, rbelWriter).getBytes();
  }

  @Override
  public byte[] renderNode(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    return render(node, rbelWriter);
  }

  public String renderToString(RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    if (isJsonArray(node)) {
      StringJoiner joiner = new StringJoiner(",");
      for (RbelContentTreeNode childNode : node.getChildNodes()) {
        joiner.add(renderToString(childNode, rbelWriter));
      }
      return "[" + joiner + "]";

    } else if (isPrimitive(node) || !node.hasTypeOptional(RbelContentType.JSON).orElse(true)) {
      if (isStringPrimitive(node)) {
        return "\"" + getStringContentForNode(node, rbelWriter) + "\"";
      } else {
        return getStringContentForNode(node, rbelWriter);
      }
    } else if (isJsonObject(node)) {
      StringJoiner joiner = new StringJoiner(",");
      for (RbelContentTreeNode childNode : node.getChildNodes()) {
        joiner.add(
            "\""
                + childNode.getKey().orElseThrow()
                + "\": "
                + renderToString(childNode, rbelWriter));
      }
      return "{" + joiner + "}";
    } else {
      throw new RbelSerializationException("Failed to serialize the node: " + node);
    }
  }

  private static String getStringContentForNode(
      RbelContentTreeNode node, RbelWriterInstance rbelWriter) {
    if (node.getChildNodes().isEmpty()) {
      if (node.getContent() == null) {
        return "{}";
      }
      return new String(node.getContent(), node.getElementCharset());
    } else {
      return new String(rbelWriter.renderTree(node).getContent(), node.getElementCharset());
    }
  }

  public static boolean isJsonArray(RbelContentTreeNode node) {
    return node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_ARRAY);
  }

  private boolean isJsonObject(RbelContentTreeNode node) {
    return !node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_ARRAY);
  }

  private boolean isStringPrimitive(RbelContentTreeNode node) {
    return !node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_NON_STRING_PRIMITIVE);
  }

  private boolean isPrimitive(RbelContentTreeNode node) {
    return node.attributes().containsKey(RbelJsonElementToNodeConverter.JSON_PRIMITIVE)
        || node.getChildNodes().isEmpty();
  }
}
