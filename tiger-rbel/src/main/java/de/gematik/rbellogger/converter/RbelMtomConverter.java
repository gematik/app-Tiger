/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import com.google.common.net.MediaType;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.util.MtomPart;
import de.gematik.rbellogger.util.RbelException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.*;

@ConverterInfo(dependsOn={RbelXmlConverter.class})
@Slf4j
public class RbelMtomConverter implements RbelConverterPlugin {

  public static final String CONTENT_ID = "Content-ID";

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    if (!stringStartIsMtom(rbelElement)) {
      return;
    }
    new RbelMtomConverterExecutor(rbelElement, converter).execute();
  }

  private boolean stringStartIsMtom(RbelElement rbelElement) {
    return rbelElement.getRawStringContent().trim().startsWith("--");
  }

  @RequiredArgsConstructor
  private class RbelMtomConverterExecutor {

    private final RbelElement parentNode;
    private final RbelConverter converter;
    private List<MtomPart> mtomParts;
    private Map<String, String> dataParts = new HashMap<>();
    private MtomPart rootPart;

    public void execute() {
      final Optional<MediaType> vauContentType =
          getVauContentType(parentNode).or(() -> getMtomContentType(parentNode));
      if (vauContentType.isEmpty()
          || !vauContentType.get().is(MediaType.parse("multipart/related"))) {
        return;
      }

      mtomParts = divideMessageIntoMtomParts(parentNode, vauContentType.get());
      if (mtomParts.isEmpty()) {
        return;
      }

      rootPart =
          mtomParts.stream()
              .filter(
                  mtomPart ->
                      mtomPart
                          .getMessageHeader()
                          .getOrDefault(CONTENT_ID, "")
                          .equals(
                              vauContentType.get().parameters().get("start").stream()
                                  .findAny()
                                  .orElse("")))
              .findAny()
              .orElse(mtomParts.get(0));
      if (rootPart == null) {
        log.warn("Skipping MTOM/XOP reconstruction: Unable to find root-part!");
        return;
      }

      final Optional<RbelMtomFacet> mtomFacet = reconstructMessage();
      if (mtomFacet.isEmpty()) {
        log.warn("Skipping MTOM/XOP reconstruction: Message reconstruction failed!");
        return;
      }
      parentNode.addFacet(mtomFacet.get());
    }

    private Optional<RbelMtomFacet> reconstructMessage() {
      try {
        final Document document = DocumentHelper.parseText(rootPart.getMessageContent());

        Map<String, String> mtomMap =
            mtomParts.stream()
                .filter(part -> StringUtils.isNotEmpty(part.getMessageHeader().get(CONTENT_ID)))
                .collect(
                    Collectors.toMap(
                        p -> p.getMessageHeader().get(CONTENT_ID), MtomPart::getMessageContent));

        final XPath xPath = DocumentHelper.createXPath("//xop:Include");
        xPath.setNamespaceURIs(Map.of("xop", "http://www.w3.org/2004/08/xop/include"));
        final List<Node> includeNodes = xPath.selectNodes(document);

        for (Node includeNode : includeNodes) {
          if (includeNode instanceof Element) {
            final Optional<String> partId =
                extractContentIdFromInclude(includeNode).map(id -> "<" + id + ">");
            if (partId.isPresent()) {
              final Optional<Node> newNode = partId.map(mtomMap::get).flatMap(this::parseAsXml);
              String part = mtomMap.remove(partId.get());
              if (newNode.isPresent()) {
                List<Node> elepar = includeNode.getParent().content();
                elepar.set(elepar.indexOf(includeNode), newNode.get());
              } else {
                // data part. store separately
                dataParts.put(includeNode.getPath(), part);
              }
            }
          }
        }
        return Optional.of(buildMtomFacet(document.asXML()));
      } catch (DocumentException | RbelException e) {
        return Optional.empty();
      }
    }

    private RbelMtomFacet buildMtomFacet(String reconstructedXml) {
      return new RbelMtomFacet(
          RbelElement.wrap(parentNode, rootPart.getMessageHeader().get("Content-Type")),
          converter.convertElement(reconstructedXml, parentNode),
          createMtomDataPartsElement());
    }

    private @Nullable RbelElement createMtomDataPartsElement() {
      if (dataParts.isEmpty()) {
        return null;
      }
      RbelElement dataPartsElement = new RbelElement(null, parentNode);
      final RbelListFacet dataListFacet =
          new RbelListFacet(
              dataParts.entrySet().stream()
                  .map(
                      dataEntry ->
                          buildDataEntry(
                              dataEntry.getValue(), dataEntry.getKey(), dataPartsElement))
                  .toList());

      dataPartsElement.addFacet(dataListFacet);
      return dataPartsElement;
    }

    private RbelElement buildDataEntry(String content, String xpath, RbelElement parentNode) {
      final RbelElement result = new RbelElement(null, parentNode);
      final RbelElement contentElement = converter.convertElement(content, result);
      contentElement.addFacet(new RbelBinaryFacet());
      result.addFacet(new RbelMtomDataPartFacet(contentElement, RbelElement.wrap(result, xpath)));
      return result;
    }

    private Optional<Node> parseAsXml(String text) {
      try {
        return Optional.ofNullable(DocumentHelper.parseText(text).getRootElement());
      } catch (DocumentException e) {
        return Optional.empty();
      }
    }

    private Optional<String> extractContentIdFromInclude(Object includeNode) {
      try {
        return Optional.ofNullable(
            new URI(((Element) includeNode).attribute("href").getValue()).getSchemeSpecificPart());
      } catch (URISyntaxException e) {
        return Optional.empty();
      }
    }

    private List<MtomPart> divideMessageIntoMtomParts(
        RbelElement rbelElement, MediaType mediaType) {
      final List<String> boundary = mediaType.parameters().get("boundary");
      if (boundary.isEmpty()) {
        return List.of();
      }
      return Stream.of(
              rbelElement
                  .getRawStringContent()
                  .split("(\r\n|\n)--" + Pattern.quote(boundary.get(0))))
          .map(MtomPart::new)
          .filter(mtomPart -> mtomPart.getMessageHeader().size() > 0)
          .toList();
    }

    private Optional<MediaType> getVauContentType(RbelElement rbelElement) {
      return Optional.ofNullable(rbelElement.getParentNode())
          .flatMap(el -> el.getFirst("additionalHeaders"))
          .flatMap(this::extractContentType);
    }

    private Optional<MediaType> getMtomContentType(RbelElement rbelElement) {
      return Optional.ofNullable(rbelElement.getParentNode())
          .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
          .map(RbelHttpMessageFacet::getHeader)
          .flatMap(this::extractContentType);
    }

    private Optional<MediaType> extractContentType(RbelElement rbelElement) {
      return rbelElement.getFacet(RbelHttpHeaderFacet.class).stream()
          .flatMap(header -> header.getCaseInsensitiveMatches("content-type"))
          .map(RbelElement::getRawStringContent)
          .filter(Objects::nonNull)
          .map(String::trim)
          .map(
              value -> {
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
}
