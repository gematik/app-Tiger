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

import static de.gematik.rbellogger.writer.tree.RbelXmlElementToNodeConverter.IS_XML_ATTRIBUTE;
import static de.gematik.rbellogger.writer.tree.RbelXmlElementToNodeConverter.IS_XML_NAMESPACE_PREFIX;
import static de.gematik.rbellogger.writer.tree.RbelXmlElementToNodeConverter.XML_NAMESPACE_PREFIX;
import static de.gematik.rbellogger.writer.tree.RbelXmlElementToNodeConverter.XML_NAMESPACE_URI;

import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import java.io.IOException;
import java.io.StringWriter;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.DefaultText;

@AllArgsConstructor
@Slf4j
public class RbelXmlSerializer implements RbelSerializer {

  @SneakyThrows
  public byte[] render(RbelContentTreeNode treeRootNode, RbelWriterInstance rbelWriter) {
    final Document document = DocumentHelper.createDocument();

    for (RbelContentTreeNode childNode : treeRootNode.getChildNodes()) {
      addNode(childNode, document, rbelWriter);
    }

    addEncodingInformationIfMissing(document, treeRootNode);

    return convertDocumentToString(document, false);
  }

  @SneakyThrows
  public byte[] renderNode(RbelContentTreeNode treeRootNode, RbelWriterInstance rbelWriter) {
    final Document document = DocumentHelper.createDocument();
    for (RbelContentTreeNode childNode : treeRootNode.getChildNodes()) {
      addNode(childNode, document, rbelWriter);
    }
    return convertDocumentToString(document, true);
  }

  private void addEncodingInformationIfMissing(
      Document document, RbelContentTreeNode treeRootNode) {
    if (StringUtils.isEmpty(document.getXMLEncoding())) {
      document.setXMLEncoding(treeRootNode.getElementCharset().displayName());
    }
  }

  private void addNode(
      RbelContentTreeNode treeNode, Branch parentBranch, RbelWriterInstance rbelWriter) {
    if (log.isTraceEnabled()) {
      log.trace(
          "converting xml node '{}'",
          treeNode.getContent() == null
              ? "<null>"
              : new String(treeNode.getContent(), treeNode.getElementCharset()));
    }
    final String key =
        treeNode
            .getKey()
            .orElseThrow(
                () -> new RbelSerializationException("Could not find key for " + treeNode));

    if (!StringUtils.isEmpty(key)) {
      if (isATextNode(treeNode, key)) {
        if (!(parentBranch instanceof Document)) {
          parentBranch.add(
              new DefaultText(
                  new String(
                      rbelWriter.renderTree(treeNode).getContent(), treeNode.getElementCharset())));
        }
      } else if (treeNode.attributes().containsKey(IS_XML_ATTRIBUTE)
          && parentBranch instanceof Element element) {
        element.addAttribute(key, rbelWriter.renderTree(treeNode).getContentAsString());
      } else if (treeNode.attributes().containsKey(IS_XML_NAMESPACE_PREFIX)
          && parentBranch instanceof Element element) {
        element.addNamespace(key, treeNode.getRawStringContent());
      } else {
        addXmlNode(treeNode, parentBranch, rbelWriter, key);
      }
    }
  }

  private void addXmlNode(
      RbelContentTreeNode treeNode,
      Branch parentBranch,
      RbelWriterInstance rbelWriter,
      String key) {
    final Element newElement = parentBranch.addElement(determineQualifiedName(treeNode, key));

    for (RbelContentTreeNode childNode : treeNode.getChildNodes()) {
      addNode(childNode, newElement, rbelWriter);
    }
  }

  private static boolean isATextNode(RbelContentTreeNode treeNode, String key) {
    return "text".equals(key)
        && !treeNode.hasTypeOptional(RbelContentType.XML).orElse(false)
        && !treeNode.attributes().containsKey(IS_XML_ATTRIBUTE);
  }

  private QName determineQualifiedName(RbelContentTreeNode treeNode, String key) {
    if (treeNode.attributes().containsKey(XML_NAMESPACE_URI)) {
      if (treeNode.attributes().containsKey(XML_NAMESPACE_PREFIX)) {
        return DocumentHelper.createQName(
            key,
            Namespace.get(
                treeNode.attributes().get(XML_NAMESPACE_PREFIX),
                treeNode.attributes().get(XML_NAMESPACE_URI)));
      } else {
        return DocumentHelper.createQName(
            key, Namespace.get(treeNode.attributes().get(XML_NAMESPACE_URI)));
      }
    } else {
      return DocumentHelper.createQName(key);
    }
  }

  private byte[] convertDocumentToString(Document document, boolean suppressDeclaration)
      throws IOException {
    final StringWriter resultWriter = new StringWriter();

    OutputFormat format = OutputFormat.createPrettyPrint();
    format.setSuppressDeclaration(suppressDeclaration);

    XMLWriter writer = new XMLWriter(resultWriter, format);
    writer.write(document);
    return resultWriter.toString().getBytes();
  }
}
