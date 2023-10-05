/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

import de.gematik.rbellogger.writer.RbelWriter.RbelWriterInstance;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.rbellogger.writer.tree.RbelXmlElementToNodeConverter;
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
            log.trace("converting xml node '{}'",
                treeNode.getContent() == null ? "<null>"
                    : new String(treeNode.getContent(), treeNode.getElementCharset()));
        }
        final String key = treeNode.getKey()
            .orElseThrow(() -> new RbelSerializationException("Could not find key for " + treeNode));

        if (StringUtils.isEmpty(key)) {
            return;
        } else {
            final boolean isATextNode = "text".equals(key)
                && !treeNode.hasTypeOptional(RbelContentType.XML).orElse(false)
                && !treeNode.attributes().containsKey(RbelXmlElementToNodeConverter.IS_XML_ATTRIBUTE);
            if (isATextNode) {
                if (!(parentBranch instanceof Document)) {
                    parentBranch.add(new DefaultText(
                        new String(rbelWriter.renderTree(treeNode).getContent(), treeNode.getElementCharset())));
                }
                return;
            } else if (treeNode.attributes().containsKey("isXmlAttribute") && parentBranch instanceof Element element) {
                element.addAttribute(key, rbelWriter.renderTree(treeNode).getContentAsString());
                return;
            }
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
