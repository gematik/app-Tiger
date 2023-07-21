/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor
@Slf4j
public class PlaywrightApp {

    public static String getPort() throws IOException {
        FileInputStream fis = new FileInputStream("../workflowui.port");
        return IOUtils.toString(fis, "UTF-8");
    }

    public static String getCurrentVersion() throws IOException {
        String content = FileUtils.readFileToString(new File("pom.xml"),
            StandardCharsets.UTF_8);
        DOMParser parser = new DOMParser();
        try
        {
            parser.parse(new InputSource(new java.io.StringReader(content)));
            Document doc = parser.getDocument();
            doc.getDocumentElement().normalize();
            if (doc != null) {
                NodeList depList = doc.getElementsByTagName("parent");
                if (depList.item(0).getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) depList.item(0);
                    NodeList versionNode = element.getElementsByTagName("version");
                    if (versionNode != null && versionNode.getLength() > 0) {
                        return versionNode.item(0).getTextContent();
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("Exception while parsing pom xml string to Document", e);
        }
        return null;
    }
}
