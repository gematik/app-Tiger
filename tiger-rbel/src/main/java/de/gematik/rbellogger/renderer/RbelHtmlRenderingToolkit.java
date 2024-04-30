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

package de.gematik.rbellogger.renderer;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static j2html.TagCreator.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelRenderingException;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import j2html.TagCreator;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import j2html.tags.EmptyTag;
import j2html.tags.Tag;
import j2html.tags.UnescapedText;
import j2html.tags.specialized.DivTag;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.bouncycastle.util.encoders.Hex;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.json.JSONObject;
import org.jsoup.Jsoup;

@RequiredArgsConstructor
@Slf4j
public class RbelHtmlRenderingToolkit {

  public static final String CLS_HEADER = "is-primary";
  public static final String CLS_BODY = "is-info";
  public static final String CLS_PKIOK = "is-success";
  public static final String CLS_PKINOK = "is-primary";
  private static final String HEX_STYLE =
      "display: inline-flex;padding-bottom: 0.2rem;padding-top: 0.2rem;white-space: revert;";
  public static final String JSON_NOTE = "json-note";

  @Getter
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

  @Getter private final Map<UUID, JsonNoteEntry> noteTags = new HashMap<>();
  private final RbelHtmlRenderer rbelHtmlRenderer;
  private static final TigerTypedConfigurationKey<String> logoFilePath =
      new TigerTypedConfigurationKey<>(
          new TigerConfigurationKey("tiger", "lib", "rbelLogoFilePath"), String.class);

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag icon(final String iconName) {
    return span().withClass("icon").with(i().withClass("fas " + iconName));
  }

  public static String prettyPrintXml(final String content) {
    try {
      final OutputFormat format = OutputFormat.createPrettyPrint();
      final org.dom4j.Document document = DocumentHelper.parseText(content);
      final StringWriter sw = new StringWriter();
      final XMLWriter writer = new XMLWriter(sw, format);
      writer.write(document);
      return sw.getBuffer().toString();
    } catch (final Exception e) {
      try {
        return Jsoup.parse(content).html();
      } catch (Exception e2) {
        log.debug("Exception while pretty-printing {}", content);
        return content;
      }
    }
  }

  public static List<DivTag> addNotes(final RbelElement el, final String... extraClasses) {
    final String className = StringUtils.join(extraClasses, " ");
    return el.getNotes().stream().map(note -> createNote(className, note)).toList();
  }

  public static DivTag createNote(String className, RbelNoteFacet note) {
    return div(i().with(new UnescapedText(note.getValue().replace("\n", "<br/>"))))
        .withClass(
            "is-family-primary has-text-weight-light m-3 "
                + className
                + " "
                + note.getStyle().toCssClass())
        .withStyle("word-break: normal;");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static EmptyTag link2CSS(final String url) {
    return link().attr("rel", "stylesheet").withHref(url);
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag ancestorTitle() {
    return div().withClass("tile is-ancestor pe-3");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag vertParentTitle() {
    return div().withClass("tile is-vertical is-parent pe-3");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag childBoxNotifTitle(final String addClasses) {
    return div().withClass("tile is-child box notification pe-3 " + addClasses);
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag t1ms(final String text) {
    return h1(text).withClass("font-monospace title");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag t2(final String text) {
    return h2(text).withClass("title");
  }

  public DomContent constructMessageId(final RbelElement message) {
    if (message.getParentNode() != null) {
      return span();
    }
    return span(getElementSequenceNumber(message))
        .withClass("msg-sequence tag is-info is-light me-3 is-size-4 test-message-number");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public ContainerTag convert(final RbelElement element) {
    return convert(element, Optional.empty());
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  private static ContainerTag addNotes(RbelElement element, ContainerTag elementTag) {
    elementTag.with(
        element.getFacets().stream()
            .filter(RbelNoteFacet.class::isInstance)
            .map(RbelNoteFacet.class::cast)
            .map(RbelNoteFacet::renderToHtml)
            .toList());
    return elementTag;
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public ContainerTag convert(final RbelElement element, final Optional<String> key) {
    if (element.getRawContent() != null
        && !shouldRenderEntitiesWithSize(element.getRawContent().length)) {
      return addNotes(
          element,
          span(RbelHtmlRenderer.buildOversizeReplacementString(element)).withClass("is-size-7"));
    }

    return convertUnforced(element, key)
        .orElseGet(
            () -> {
              if (element.hasFacet(RbelBinaryFacet.class)) {
                return addNotes(element, printAsBinary(element));
              } else {
                return addNotes(
                    element, span(performElementToTextConversion(element)).withClass("is-size-7"));
              }
            });
  }

  private String performElementToTextConversion(final RbelElement el) {
    return rbelHtmlRenderer
        .getRbelValueShader()
        .shadeValue(el, el.findKeyInParentElement())
        .or(
            () ->
                Optional.ofNullable(el)
                    .map(RbelElement::getRawStringContent)
                    .map(str -> str.replace("\n", "<br/>")))
        .orElse("");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public Optional<ContainerTag> convertUnforced(
      final RbelElement element, final Optional<String> key) {
    return rbelHtmlRenderer.convert(element, key, this);
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public Optional<String> shadeValue(RbelElement element, Optional<String> key) {
    return rbelHtmlRenderer.getRbelValueShader().shadeValue(element, key);
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public ContainerTag printAsBinary(final RbelElement el) {
    return div(pre()
            .withStyle(HEX_STYLE)
            .withText(
                "Offset    "
                    + " | "
                    + " 0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f"
                    + " | "
                    + "ASCII Text      "))
        .with(
            IntStream.range(0, (el.getRawContent().length + 15) / 16)
                .limit(30)
                .mapToObj(
                    line ->
                        div(
                            pre()
                                .withStyle(HEX_STYLE)
                                .withText(
                                    StringUtils.leftPad(Integer.toHexString(line * 16), 8, '0')
                                        + "  "
                                        + " | "
                                        + getLineAsHexString(el.getRawContent(), line)
                                        + " | "
                                        + getLineAsAsciiString(el.getRawContent(), line))))
                .toList());
  }

  private String getLineAsHexString(byte[] rawContent, int start) {
    return StringUtils.rightPad(
        IntStream.range(start * 16, Math.min((start + 1) * 16, rawContent.length))
            .mapToObj(index -> Hex.toHexString(rawContent, index, 1))
            .collect(Collectors.joining(" ")),
        16 * 3 - 1,
        ' ');
  }

  private String getLineAsAsciiString(byte[] rawContent, int start) {
    return StringUtils.rightPad(
        IntStream.range(start * 16, Math.min((start + 1) * 16, rawContent.length))
            .mapToObj(index -> rawContent[index])
            .filter(Objects::nonNull)
            .map(
                bit ->
                    new String(new byte[] {bit}, StandardCharsets.US_ASCII)
                        .replaceAll("[^ -~]", "."))
            .collect(Collectors.joining("")),
        16,
        ' ');
  }

  public DomContent renderMenu() {
    return div()
        .withClass(" col is-one-fifth menu is-size-4 sidebar")
        .with(
            a(i().withClass("fas fa-angle-double-up"))
                .withId("collapse-all")
                .withHref("#")
                .withClass("float-end me-3"),
            a(i().withClass("fas fa-angle-double-down"))
                .withId("expand-all")
                .withHref("#")
                .withClass("float-end me-3"),
            h2("Flow").withClass("mb-3 ms-2"),
            div().withClass("ms-1").withId("sidebar-menu"));
  }

  public String menuTab(final RbelElement rbelElement) {
    final String uuid = rbelElement.getUuid();
    JSONObject metaData = new JSONObject();
    metaData.put("uuid", uuid);
    metaData.put("sequenceNumber", Integer.parseInt(getElementSequenceNumber(rbelElement)) - 1);
    if (rbelElement.hasFacet(RbelRequestFacet.class)) {
      metaData.put(
          "menuInfoString", rbelElement.getFacetOrFail(RbelRequestFacet.class).getMenuInfoString());
    } else {
      metaData.put(
          "menuInfoString",
          rbelElement
              .getFacet(RbelResponseFacet.class)
              .map(RbelResponseFacet::getMenuInfoString)
              .orElse(""));
    }
    metaData.put(
        "timestamp",
        rbelElement
            .getFacet(RbelMessageTimingFacet.class)
            .map(RbelMessageTimingFacet::getTransmissionTime)
            .orElse(null));
    metaData.put("isRequest", rbelElement.hasFacet(RbelRequestFacet.class));
    return "createMenuEntry(" + metaData.toString() + ")";
  }

  private String getElementSequenceNumber(RbelElement rbelElement) {
    return rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getSequenceNumber)
        .map(zeroBased -> zeroBased + 1)
        .map(Object::toString)
        .orElse("0");
  }

  public String renderDocument(List<RbelElement> elements, boolean localRessources)
      throws IOException {
    return TagCreator.document(
        html(
                head(
                    meta().attr("charset", "utf-8"),
                    meta()
                        .attr("name", "viewport")
                        .attr("content", "width=device-width, initial-scale=1"),
                    title().withText(rbelHtmlRenderer.getTitle()),
                    script()
                        .withSrc(
                            localRessources
                                ? "../webjars/sockjs-client/sockjs.min.js"
                                : "https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"),
                    script()
                        .withSrc(
                            localRessources
                                ? "../webjars/stomp-websocket/stomp.min.js"
                                : "https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"),
                    script()
                        .withSrc(
                            localRessources
                                ? "../webjars/jquery/jquery.min.js"
                                : "https://code.jquery.com/jquery-1.12.4.js"),
                    script()
                        .withSrc(
                            localRessources
                                ? "../webjars/bootstrap/js/bootstrap.bundle.min.js"
                                : "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"),
                    script()
                        .withSrc(
                            localRessources
                                ? "../webjars/highlightjs/highlight.min.js"
                                : "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.7.0/build/highlight.min.js"),
                    script()
                        .withSrc(
                            localRessources
                                ? "../webjars/highlightjs/languages/xml.min.js"
                                : "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.7.0/build/languages/xml.min.js"),
                    script()
                        .withSrc(
                            localRessources
                                ? "../webjars/dayjs/dayjs.min.js"
                                : "https://cdnjs.cloudflare.com/ajax/libs/dayjs/1.11.10/dayjs.min.js"),
                    link2CSS(
                        localRessources
                            ? "../webjars/bootstrap/css/bootstrap.min.css"
                            : "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css"),
                    link2CSS(
                        localRessources
                            ? "../webjars/highlightjs/styles/stackoverflow-dark.min.css"
                            : "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.7.0/build/styles/stackoverflow-dark.min.css"),
                    link2CSS(
                        localRessources
                            ? "../webjars/font-awesome/css/all.min.css"
                            : "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css"),
                    link().withRel("icon").withType("image/png").withHref(getLogoBase64Str()),
                    tag("style")
                        .with(
                            new UnescapedText(
                                IOUtils.resourceToString("/rbel.css", StandardCharsets.UTF_8)))),
                body()
                    .with(
                        div().withId("navbardiv"),
                        section()
                            .withClass("main-content")
                            .with(
                                section()
                                    .withClass("row header")
                                    .with(
                                        div()
                                            .withClass("col-1 h-100 my-auto logo")
                                            .with(
                                                img()
                                                    .withSrc(getLogoBase64Str())
                                                    .withId("test-tiger-logo")),
                                        div()
                                            .withClass("col is-size-6")
                                            .with(
                                                div()
                                                    .withClass("row my-auto")
                                                    .with(
                                                        div(rbelHtmlRenderer.getTitle())
                                                            .withClass(
                                                                "col navbar-title is-size-3 h-100"
                                                                    + " my-auto"),
                                                        span(rbelHtmlRenderer.getVersionInfo())
                                                            .withClass(
                                                                "col-2 is-size-7 navbar-version")),
                                                div(
                                                    new UnescapedText(
                                                        rbelHtmlRenderer.getSubTitle()))))),
                        section()
                            .withClass("row is-fullheight")
                            .withId("test-rbel-section")
                            .with(
                                renderMenu(),
                                div()
                                    .withClass("col ms-6")
                                    .with(
                                        div("Created "
                                                + DateTimeFormatter.RFC_1123_DATE_TIME.format(
                                                    ZonedDateTime.now()))
                                            .withClass(
                                                "created fst-italic is-size-6 float-end me-6"),
                                        div()
                                            .with(
                                                elements.stream()
                                                    //
                                                    // .filter(el ->
                                                    // el.hasFacet(RbelTcpIpMessageFacet.class))
                                                    .map(this::convertMessage)
                                                    .toList()),
                                        div("Created "
                                                + DateTimeFormatter.RFC_1123_DATE_TIME.format(
                                                    ZonedDateTime.now()))
                                            .withClass(
                                                "created fst-italic is-size-6 float-end me-6")))))
            .with(
                script()
                    .with(
                        new UnescapedText(
                            IOUtils.resourceToString("/rbel.js", StandardCharsets.UTF_8)))
                    .attr("id", "mainWebUiScript"),
                script(elements.stream().map(this::menuTab).collect(Collectors.joining("\n")))));
  }

  private String getLogoBase64Str() {
    return logoFilePath
        .getValue()
        .filter(StringUtils::isNotEmpty)
        .map(
            filePath -> {
              try {
                final byte[] bytes = FileUtils.readFileToByteArray(new File(filePath));
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
              } catch (IOException e) {
                throw new RbelRenderingException("Could not load file", e);
              }
            })
        .orElseGet(
            () -> {
              try {
                return IOUtils.resourceToString(
                    "/tiger-monochrome-64.png.base64", StandardCharsets.UTF_8);
              } catch (IOException e) {
                throw new RbelRenderingException("Could not load file", e);
              }
            });
  }

  public DomContent convertMessage(RbelElement element) {
    return div(new RbelMessageRenderer().performRendering(element, Optional.empty(), this));
  }

  public JsonNode shadeJson(
      final JsonNode input, final Optional<String> key, final RbelElement originalElement) {
    if (input.isValueNode()) {
      return shadeJsonPrimitive(input, key, originalElement);
    } else if (input.isObject()) {
      return shadeJsonObject(input, originalElement);
    } else if (input.isArray()) {
      return shadeJsonArray(input, key, originalElement);
    } else if (input.isNull()) {
      return input;
    } else {
      throw new RbelRenderingException("Unshadeable JSON-Type " + input.getClass().getSimpleName());
    }
  }

  private JsonNode shadeJsonArray(
      JsonNode input, Optional<String> key, RbelElement originalElement) {
    final ArrayNode output = objectMapper.createArrayNode();
    if (originalElement.hasFacet(RbelNoteFacet.class)) {
      final UUID uuid = UUID.randomUUID();
      noteTags.put(
          uuid,
          JsonNoteEntry.builder()
              .stringToMatch("\"" + uuid + "\"")
              .tagForKeyReplacement(span())
              .tagForValueReplacement(
                  span()
                      .with(
                          originalElement.getNotes().stream()
                              .map(
                                  note ->
                                      div(i(note.getValue()))
                                          .withClass(note.getStyle().toCssClass()))
                              .toList())
                      .withClass(JSON_NOTE))
              .build());
      output.add(uuid.toString());
    }
    for (int i = 0; i < input.size(); i++) {
      final int finalI = i;
      final List<? extends RbelElement> rbelListElements =
          originalElement.getFacetOrFail(RbelListFacet.class).getChildNodes();
      output.add(shadeJson(input.get(i), key.map(v -> v + "." + finalI), rbelListElements.get(i)));
    }
    return output;
  }

  private JsonNode shadeJsonObject(JsonNode input, RbelElement originalElement) {
    final ObjectNode output = objectMapper.createObjectNode();
    if (originalElement.hasFacet(RbelNoteFacet.class)) {
      final UUID uuid = UUID.randomUUID();

      noteTags.put(
          uuid,
          JsonNoteEntry.builder()
              .stringToMatch("\"note\" : \"" + uuid + "\"" + (input.isEmpty() ? "" : ","))
              .tagForKeyReplacement(span())
              .tagForValueReplacement(
                  span()
                      .with(
                          originalElement.getNotes().stream()
                              .map(
                                  note ->
                                      div(i(note.getValue()))
                                          .withClass(note.getStyle().toCssClass()))
                              .toList())
                      .withClass(JSON_NOTE))
              .build());
      output.put("note", uuid.toString());
    }
    for (Iterator<Entry<String, JsonNode>> it = input.fields(); it.hasNext(); ) {
      Entry<String, JsonNode> element = it.next();
      output.set(
          element.getKey(),
          shadeJson(
              element.getValue(),
              Optional.of(element.getKey()),
              originalElement
                  .getFirst(element.getKey())
                  .orElseThrow(
                      () ->
                          new RuntimeException(
                              "Unable to find matching Element for key " + element.getKey()))));
    }
    return output;
  }

  private JsonNode shadeJsonPrimitive(
      JsonNode input, Optional<String> key, RbelElement originalElement) {
    final JsonNode jsonElement =
        rbelHtmlRenderer
            .getRbelValueShader()
            .shadeValue(input, key)
            .map(shadedValue -> (JsonNode) new TextNode(StringEscapeUtils.escapeHtml4(shadedValue)))
            .orElse(input);

    if (!originalElement.getNotes().isEmpty()) {
      final UUID uuid = UUID.randomUUID();
      noteTags.put(
          uuid,
          JsonNoteEntry.builder()
              .stringToMatch("\"" + uuid + "\"")
              .tagForKeyReplacement(span(jsonElement.toString()))
              .tagForValueReplacement(
                  span()
                      .with(
                          originalElement.getNotes().stream()
                              .map(
                                  note ->
                                      div(i(text(note.getValue())))
                                          .withClass(note.getStyle().toCssClass()))
                              .toList())
                      .withClass(JSON_NOTE))
              .build());
      return new TextNode(uuid.toString());
    } else {
      return jsonElement;
    }
  }

  public List<? extends ContainerTag> convertNested(final RbelElement el) {
    return el.traverseAndReturnNestedMembers().stream()
        .filter(entry -> !entry.getFacets().isEmpty())
        .map(
            child ->
                Pair.of(
                    child,
                    rbelHtmlRenderer.isRenderNestedObjectsWithoutFacetRenderer()
                        ? Optional.of(convert(child, Optional.empty()))
                        : convertUnforced(child, Optional.empty())))
        .filter(pair -> pair.getValue().isPresent())
        .map(
            pair ->
                article()
                    .withClass("message is-ancestor notification is-warning my-6 py-3 px-3")
                    .with(
                        div(h2(pair.getKey().findNodePath())
                                .withClass("title")
                                .withStyle("word-break: keep-all;"))
                            .withClass("message-header")
                            .with(addNotes(pair.getKey()))
                            .with(showContentButtonAndDialog(pair.getKey(), this)),
                        div(div(pair.getValue()
                                    .get()
                                    .withClass("notification tile is-child box pe-3"))
                                .withClass("notification tile is-parent pe-3"))
                            .withClass("message-body px-0")))
        .toList();
  }

  public List<DomContent> packAsInfoLine(String parameterName, DomContent... contentObject) {
    return List.of(
        div()
            .withClass("row is-multiline is-mobile")
            .with(
                div().withClass("col-sm-4").with(b().withText(parameterName + ": ")),
                div().withClass("col").with(contentObject)));
  }

  public DomContent formatHex(RbelElement value) {
    return span()
        .withText(Hex.toHexString(value.getRawContent()))
        .withStyle("font-family: monospace; padding-right: 0.3rem;");
  }

  public DomContent formatHexAlike(String value) {
    return span().withText(value).withStyle("font-family: monospace; padding-right: 0.3rem;");
  }

  public boolean shouldRenderEntitiesWithSize(int length) {
    return rbelHtmlRenderer.getMaximumEntitySizeInBytes() > length;
  }

  @Builder
  @AllArgsConstructor
  @Getter
  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static class JsonNoteEntry {

    private final String stringToMatch;
    private final Tag tagForKeyReplacement;
    private final Tag tagForValueReplacement;
  }
}
