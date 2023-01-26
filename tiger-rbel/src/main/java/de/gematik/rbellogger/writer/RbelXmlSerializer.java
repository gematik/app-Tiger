/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.writer;

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
    public byte[] render(RbelContentTreeNode treeRootNode, RbelWriter rbelWriter) {
        final Document document = DocumentHelper.createDocument();
        for (RbelContentTreeNode childNode : treeRootNode.childNodes()) {
            addNode(childNode, document, rbelWriter);
        }
        return convertDocumentToString(document);
    }

    private void addNode(RbelContentTreeNode treeNode, Branch parentBranch, RbelWriter rbelWriter) {
        if (log.isTraceEnabled()) {
            log.trace("converting xml node '{}'", treeNode.getContent() == null ? "<null>" : new String(treeNode.getContent()));
        }
        final String key = treeNode.getKey();

        if (StringUtils.isEmpty(key)) {
            return;
        } else if ("text".equals(key) || !treeNode.hasTypeOptional("xml").orElse(true)) {
            if (!(parentBranch instanceof Document)) {
                parentBranch.add(new DefaultText(new String(rbelWriter.renderTree(treeNode), treeNode.getCharset())));
            }
            return;
        } else if (treeNode.attributes().containsKey("isXmlAttribute") && parentBranch instanceof Element) {
            ((Element) parentBranch).addAttribute(key, new String(rbelWriter.renderTree(treeNode)));
            return;
        }

        final Element newElement = parentBranch.addElement(key);
        for (RbelContentTreeNode childNode : treeNode.childNodes()) {
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
