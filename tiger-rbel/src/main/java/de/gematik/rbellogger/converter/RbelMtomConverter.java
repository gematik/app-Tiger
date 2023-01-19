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

package de.gematik.rbellogger.converter;

import com.google.common.net.MediaType;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelMtomFacet;
import de.gematik.rbellogger.data.util.MtomPart;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.*;

@Slf4j
public class RbelMtomConverter implements RbelConverterPlugin {

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        if (!stringStartIsMtom(rbelElement)) {
            return;
        }
        final Optional<MediaType> vauContentType = getVauContentType(rbelElement);
        if (vauContentType.isEmpty()
            || !vauContentType.get().is(MediaType.parse("multipart/related"))) {
            return;
        }

        List<MtomPart> mtomParts = divideMessageIntoMtomParts(rbelElement, vauContentType.get());
        if (mtomParts.size() == 0) {
            return;
        }

        MtomPart rootPart = mtomParts.stream()
            .filter(mtomPart -> mtomPart.getMessageHeader()
                .getOrDefault("Content-ID", "")
                .equals(vauContentType.get().parameters().get("start").get(0)))
            .findAny().orElse(null);
        if (rootPart == null) {
            log.warn("Skipping MTOM/XOP reconstruction: Unable to find root-part!");
            return;
        }

        final String contentType = rootPart.getMessageHeader().get("Content-Type");
        final Optional<String> reconstructedMessage = reconstructMessage(rootPart, mtomParts);
        if (reconstructedMessage.isEmpty()) {
            log.warn("Skipping MTOM/XOP reconstruction: Message reconstruction failed!");
            return;
        }
        rbelElement.addFacet(new RbelMtomFacet(
            RbelElement.wrap(rbelElement, contentType),
            converter.convertElement(reconstructedMessage.get().getBytes(StandardCharsets.UTF_8), rbelElement),
            mtomParts));
    }

    private Optional<String> reconstructMessage(MtomPart rootPart, List<MtomPart> mtomParts) {
        try {
            final Document document = DocumentHelper.parseText(rootPart.getMessageContent());

            Map<String, String> mtomMap = mtomParts.stream()
                .collect(Collectors.toMap(
                    p -> p.getMessageHeader().get("Content-ID"),
                    MtomPart::getMessageContent));

            final XPath xPath = DocumentHelper.createXPath("//xop:Include");
            xPath.setNamespaceURIs(Map.of("xop", "http://www.w3.org/2004/08/xop/include"));
            final List<Node> includeNodes = xPath.selectNodes(document);

            for (Node includeNode : includeNodes) {
                if (includeNode instanceof Element) {
                    Element newEl = DocumentHelper.parseText(
                            mtomMap.get("<" + extractContentIdFromInclude(includeNode).get() + ">"))
                        .getRootElement();
                    List<Node> elepar = includeNode.getParent().content();
                    elepar.set(elepar.indexOf(includeNode), newEl);
                }
            }
            return Optional.ofNullable(document.asXML());
        } catch (DocumentException e) {
            return Optional.empty();
        }
    }

    private Optional<String> extractContentIdFromInclude(Object includeNode) {
        try {
            return Optional.ofNullable(
                new URI(((Element) includeNode).attribute("href").getValue())
                    .getSchemeSpecificPart());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private List<MtomPart> divideMessageIntoMtomParts(RbelElement rbelElement, MediaType mediaType) {
        final List<String> boundary = mediaType.parameters().get("boundary");
        if (boundary.isEmpty()) {
            return List.of();
        }
        return Stream.of(rbelElement.getRawStringContent()
                .split("(\r\n|\n)--" + Pattern.quote(boundary.get(0))))
            .map(MtomPart::new)
            .filter(mtomPart -> mtomPart.getMessageHeader().size() > 0)
            .collect(Collectors.toList());
    }

    private boolean stringStartIsMtom(RbelElement rbelElement) {
        return rbelElement.getRawStringContent().trim().startsWith("--");
    }

    private Optional<MediaType> getVauContentType(RbelElement rbelElement) {
        return Optional.ofNullable(rbelElement.getParentNode())
            .flatMap(el -> el.getFirst("additionalHeaders"))
            .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
            .stream()
            .flatMap(header -> header.getCaseInsensitiveMatches("content-type"))
            .map(RbelElement::getRawStringContent)
            .filter(Objects::nonNull)
            .map(String::trim)
            .map(value -> {
                try {
                    return MediaType.parse(value);
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .findAny();
    }

}
