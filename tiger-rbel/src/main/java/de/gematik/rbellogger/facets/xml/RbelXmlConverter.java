/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.facets.xml;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.http.RbelHttpRequestConverter;
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import de.gematik.rbellogger.util.RbelException;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.AbstractBranch;
import org.dom4j.tree.DefaultComment;
import org.dom4j.tree.DefaultProcessingInstruction;
import org.xml.sax.InputSource;

@ConverterInfo(dependsOn = {RbelHttpRequestConverter.class, RbelHttpResponseConverter.class})
@Slf4j
public class RbelXmlConverter extends RbelConverterPlugin {

  private static final String XML_TEXT_KEY = "text";
  private static final RbelHtmlConverter HTML_CONVERTER = new RbelHtmlConverter();
  private static final byte[] OPEN_TAG = "<".getBytes();
  private static final byte[] CLOSE_TAG = ">".getBytes();

  @Override
  public void consumeElement(final RbelElement rbel, final RbelConversionExecutor context) {
    final var content = rbel.getContent();
    if (!(content.startsTrimmedWith(OPEN_TAG) && content.endsTrimmedWith(CLOSE_TAG))) {
      return;
    }
    try {

      InputSource source = buildInputSource(rbel);
      final Document parsedXml = parseXml(source);
      buildXmlElementForNode(parsedXml, rbel, context);

      if (rbel.getContent().startsTrimmedWith("<?".getBytes())
          && rbel.getContent().contains("?>".getBytes())) {
        RbelElement encoding = getEncoding(parsedXml, rbel);
        RbelElement xmlVersion = getXmlVersion(rbel.getRawStringContent(), rbel);
        RbelXmlRootAttributeFacet rootAttributeFacet =
            new RbelXmlRootAttributeFacet(xmlVersion, encoding);
        rbel.addFacet(rootAttributeFacet);
      }
      setCharset(parsedXml, rbel);
      rbel.addFacet(new RbelRootFacet<>(rbel.getFacetOrFail(RbelXmlFacet.class)));
    } catch (DocumentException e) {
      log.trace(
          "Exception while trying to parse XML. Trying as HTML (more lenient SAX parsing)", e);
      HTML_CONVERTER
          .parseHtml(rbel)
          .ifPresent(
              document -> {
                HTML_CONVERTER.buildXmlElementForNode(document, rbel, context);
                rbel.addFacet(new RbelRootFacet<>(rbel.getFacetOrFail(RbelXmlFacet.class)));
              });
    }
  }

  @Override
  public boolean skipParsingOversizedContent() {
    return true;
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

  private InputSource buildInputSource(RbelElement parentElement) {
    if (parentElement.getCharset().isPresent()) {
      String text = parentElement.getRawStringContent();
      if (text == null) {
        throw new RbelConversionException("No raw string content available.");
      }
      var source = new InputSource(new StringReader(text.trim()));
      // see https://www.ietf.org/rfc/rfc3023 8.5 and 8.20: We always use the http-encoding.
      source.setEncoding(parentElement.getElementCharset().name());
      return source;
    } else {
      return new InputSource(new ByteArrayInputStream(parentElement.getRawContent()));
    }
  }

  private void buildXmlElementForNode(
      Branch branch, RbelElement parentElement, RbelConversionExecutor converter) {
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
      RbelConversionExecutor converter,
      RbelMultiMap<RbelElement> childElements) {
    for (Object child : branch.content()) {
      if (child instanceof Text text) {
        childElements.put(XML_TEXT_KEY, converter.convertElement(text.getText(), parentElement));
      } else if (child instanceof AbstractBranch abstractBranch) {
        final RbelElement element =
            new RbelElement(
                abstractBranch.asXML().getBytes(parentElement.getElementCharset()), parentElement);
        buildXmlElementForNode(abstractBranch, element, converter);
        childElements.put(abstractBranch.getName(), element);
      } else if (child instanceof Namespace namespace) {
        final String childXmlName = namespace.asXML().split("=")[0];
        final RbelElement namespaceAttributeElement =
            converter.convertElement(namespace.getText(), parentElement);
        namespaceAttributeElement.addFacet(new RbelXmlAttributeFacet());
        namespaceAttributeElement.addFacet(new RbelXmlNamespaceFacet());
        childElements.put(childXmlName, namespaceAttributeElement);
      } else if (child instanceof DefaultComment) {
        // do nothing
      } else if (child instanceof DefaultProcessingInstruction instruction) {
        final RbelElement element =
            convertProcessingInstruction(parentElement, converter, instruction);
        childElements.put(instruction.getTarget(), element);
      } else {
        throw new RbelException(
            "Could not convert XML element of type " + child.getClass().getSimpleName());
      }
    }
  }

  private static RbelElement convertProcessingInstruction(
      RbelElement parentElement,
      RbelConversionExecutor converter,
      DefaultProcessingInstruction instruction) {
    final RbelElement instructionElement =
        new RbelElement(
            instruction.asXML().getBytes(parentElement.getElementCharset()), parentElement);
    final RbelMultiMap<RbelElement> childElements = new RbelMultiMap<>();
    final RbelXmlProcessingInstructionFacet instructionFacet =
        new RbelXmlProcessingInstructionFacet(instruction.getTarget());
    instructionElement.addFacet(instructionFacet);
    instructionElement.addFacet(new RbelMapFacet(childElements));
    for (Entry<String, String> attribute : instruction.getValues().entrySet()) {
      final RbelElement value = converter.convertElement(attribute.getValue(), instructionElement);
      childElements.put(attribute.getKey(), value);
    }
    return instructionElement;
  }

  private static void addAttributes(
      Branch branch,
      RbelElement parentElement,
      RbelConversionExecutor converter,
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

  private static RbelElement getXmlVersion(String rawXml, RbelElement parent) {
    Pattern pattern = Pattern.compile("^\\s*<\\?xml\\s+version\\s*=\\s*[\"']([^\"']+)[\"']");

    Matcher matcher = pattern.matcher(rawXml);

    if (matcher.find()) {
      return new RbelElement(matcher.group(1).getBytes(), parent);
    }
    return null;
  }

  private static RbelElement getEncoding(Branch branch, RbelElement parentElement) {
    return Optional.of(branch)
        .filter(Document.class::isInstance)
        .map(Document.class::cast)
        .map(Document::getXMLEncoding)
        .map(val -> new RbelElement(val.getBytes(), parentElement))
        .orElse(null);
  }
}
