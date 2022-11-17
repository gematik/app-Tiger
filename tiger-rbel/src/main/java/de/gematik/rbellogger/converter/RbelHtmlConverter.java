/*
 * Copyright (c) 2022 gematik GmbH
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
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.xml.sax.SAXException;

@Slf4j
class RbelHtmlConverter {

    private static final String XML_TEXT_KEY = "text";

    Optional<Document> parseHtml(String text) throws IOException, SAXException {
        final String lowerCase = text.toLowerCase(Locale.ROOT);
        if (!lowerCase.contains("<html") ||
            !lowerCase.contains("</html>") ||
            !lowerCase.startsWith("<")) {
            return Optional.empty();
        } else {
            return Optional.of(Jsoup.parse(text));
        }
    }

    void buildXmlElementForNode(Node branch, RbelElement parentElement, RbelConverter converter) {
        final RbelMultiMap childElements = new RbelMultiMap();
        parentElement.addFacet(RbelXmlFacet.builder()
            .childElements(childElements)
            .build());
        for (Node childNode : branch.childNodes()) {
            if (childNode instanceof TextNode) {
                childElements
                    .put(XML_TEXT_KEY,
                        converter.convertElement(((TextNode) childNode).getWholeText(), parentElement));
            } else {
                var rbelChildElement = new RbelElement(childNode.toString().getBytes(), parentElement);
                childElements.put(childNode.nodeName(), rbelChildElement);
                buildXmlElementForNode(childNode, rbelChildElement, converter);
            }
        }
        if (branch instanceof Element) {
            for (Attribute attribute : branch.attributes()) {
                childElements.put(attribute.getKey(),
                    converter.convertElement(attribute.getValue(), parentElement));
            }
        }
    }
}
