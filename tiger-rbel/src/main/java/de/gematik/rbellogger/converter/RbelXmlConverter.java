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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelXmlAttributeFacet;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import de.gematik.rbellogger.data.facet.RbelXmlNamespaceFacet;
import de.gematik.rbellogger.util.RbelException;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.AbstractBranch;
import org.dom4j.tree.DefaultComment;
import org.xml.sax.InputSource;

@Slf4j
public class RbelXmlConverter implements RbelConverterPlugin {

  private static final String XML_TEXT_KEY = "text";
  private RbelHtmlConverter htmlConverter = new RbelHtmlConverter();

  @Override
  public void consumeElement(final RbelElement rbel, final RbelConverter context) {
    final String content = rbel.getRawStringContent();
    if (content.contains("<") && content.contains(">")) {
      try {
        InputSource source = buildInputSource(content.trim(), rbel);
        final Document parsedXml = parseXml(source);
        buildXmlElementForNode(parsedXml, rbel, context);
        setCharset(parsedXml, rbel);
        rbel.addFacet(new RbelRootFacet<>(rbel.getFacetOrFail(RbelXmlFacet.class)));
      } catch (DocumentException e) {
        log.trace(
            "Exception while trying to parse XML. Trying as HTML (more lenient SAX parsing)", e);
        htmlConverter
            .parseHtml(content.trim())
            .ifPresent(
                document -> {
                  htmlConverter.buildXmlElementForNode(document, rbel, context);
                  rbel.addFacet(new RbelRootFacet<>(rbel.getFacetOrFail(RbelXmlFacet.class)));
                });
      }
    }
  }

  private void setCharset(Document source, RbelElement rbel) {
    Optional.ofNullable(source.getXMLEncoding())
        .map(Charset::forName)
        .ifPresent(charset -> rbel.setCharset(Optional.of(charset)));
  }

  private Document parseXml(InputSource source) throws DocumentException {
    SAXReader reader = new SAXReader(); // NOSONAR
    reader.setMergeAdjacentText(true);
    return reader.read(source);
  }

  private InputSource buildInputSource(String text, RbelElement parentElement) {
    if (parentElement.getCharset().isPresent()) {
      InputSource source = new InputSource(new StringReader(text));
      // see https://www.ietf.org/rfc/rfc3023 8.5 and 8.20: We always use the http-encoding.
      source.setEncoding(parentElement.getElementCharset().name());
      return source;
    } else {
      return new InputSource(new ByteArrayInputStream(parentElement.getRawContent()));
    }
  }

  private void buildXmlElementForNode(
      Branch branch, RbelElement parentElement, RbelConverter converter) {
    final RbelMultiMap<RbelElement> childElements = new RbelMultiMap<>();
    final RbelXmlFacet xmlFacet =
        RbelXmlFacet.builder()
            .childElements(childElements)
            .namespaceUri(getNamespaceUri(branch))
            .namespacePrefix(getNamespacePrefix(branch))
            .build();
    parentElement.addFacet(xmlFacet);

    addAttributes(branch, parentElement, converter, childElements);

    addChildElements(branch, parentElement, converter, childElements);

    if (childElements.stream().map(Map.Entry::getKey).noneMatch(key -> key.equals(XML_TEXT_KEY))) {
      childElements.put(XML_TEXT_KEY, new RbelElement(new byte[] {}, parentElement));
    }
  }

  private void addChildElements(
      Branch branch,
      RbelElement parentElement,
      RbelConverter converter,
      RbelMultiMap<RbelElement> childElements) {
    for (Object child : branch.content()) {
      if (child instanceof Text text) {
        childElements.put(XML_TEXT_KEY, converter.convertElement(text.getText(), parentElement));
      } else if (child instanceof AbstractBranch abstractBranch) {
        final RbelElement element =
            new RbelElement(
                abstractBranch.asXML().getBytes(parentElement.getElementCharset()), parentElement);
        buildXmlElementForNode(abstractBranch, element, converter);
        childElements.put(((AbstractBranch) child).getName(), element);
      } else if (child instanceof Namespace namespace) {
        final String childXmlName = namespace.asXML().split("=")[0];
        final RbelElement namespaceAttributeElement = converter.convertElement(namespace.getText(), parentElement);
        namespaceAttributeElement.addFacet(new RbelXmlAttributeFacet());
        namespaceAttributeElement.addFacet(new RbelXmlNamespaceFacet());
        childElements.put(childXmlName, namespaceAttributeElement);
      } else if (child instanceof DefaultComment) {
        // do nothing
      } else {
        throw new RbelException(
            "Could not convert XML element of type " + child.getClass().getSimpleName());
      }
    }
  }

  private static void addAttributes(
      Branch branch,
      RbelElement parentElement,
      RbelConverter converter,
      RbelMultiMap<RbelElement> childElements) {
    if (branch instanceof Element element) {
      for (Attribute attribute : element.attributes()) {
        final RbelElement value = converter.convertElement(attribute.getText(), parentElement);
        value.addFacet(new RbelXmlAttributeFacet());
        childElements.put(attribute.getName(), value);
      }
    }
  }

  private static String getNamespaceUri(Branch branch) {
    return Optional.of(branch)
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .map(Element::getNamespaceURI)
        .orElse(null);
  }

  private static String getNamespacePrefix(Branch branch) {
    return Optional.of(branch)
        .filter(Element.class::isInstance)
        .map(Element.class::cast)
        .map(Element::getNamespacePrefix)
        .orElse(null);
  }
}
