/*
 * Copyright (c) 2023 gematik GmbH
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
import java.io.IOException;
import java.io.StringWriter;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Branch;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
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
        return convertDocumentToString(document);
    }

    private void addNode(RbelContentTreeNode treeNode, Branch parentBranch, RbelWriterInstance rbelWriter) {
        if (log.isTraceEnabled()) {
            log.trace("converting xml node '{}'", treeNode.getContent() == null ? "<null>" : new String(treeNode.getContent()));
        }
        final String key = treeNode.getKey().orElseThrow();

        if (StringUtils.isEmpty(key)) {
            return;
        } else if ("text".equals(key) || !treeNode.hasTypeOptional(RbelContentType.XML).orElse(true)) {
            if (!(parentBranch instanceof Document)) {
                parentBranch.add(new DefaultText(new String(rbelWriter.renderTree(treeNode).getContent(), treeNode.getElementCharset())));
            }
            return;
        } else if (treeNode.attributes().containsKey("isXmlAttribute") && parentBranch instanceof Element element) {
            element.addAttribute(key, rbelWriter.renderTree(treeNode).getContentAsString());
            return;
        }

        final Element newElement = parentBranch.addElement(key);
        for (RbelContentTreeNode childNode : treeNode.getChildNodes()) {
            addNode(childNode, newElement, rbelWriter);
        }
    }

    private byte[] convertDocumentToString(Document document) throws IOException {
        final StringWriter resultWriter = new StringWriter();
        XMLWriter writer = new XMLWriter(resultWriter, OutputFormat.createPrettyPrint());
        writer.write(document);
        return resultWriter.toString().getBytes();
    }
}
