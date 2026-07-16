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
package de.gematik.rbellogger.renderer;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.collapsibleCard;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.test.tiger.common.util.FunctionWithCheckedException.unchecked;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.exceptions.RbelRenderingException;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import j2html.Config;
import j2html.TagCreator;
import j2html.rendering.FlatHtml;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import j2html.tags.EmptyTag;
import j2html.tags.UnescapedText;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.PTag;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.bouncycastle.util.encoders.Hex;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

@Builder(access = lombok.AccessLevel.PRIVATE, toBuilder = true)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Slf4j
public class RbelHtmlRenderingToolkit {

  public static final String CLS_HEADER = "is-primary";
  public static final String CLS_BODY = "is-info";
  public static final String CLS_PKIOK = "is-success";
  public static final String CLS_PKINOK = "is-primary";
  private static final String HEX_STYLE =
      "display: inline-flex;padding-bottom: 0.2rem;padding-top: 0.2rem;white-space: revert;";
  public static final String JSON_NOTE = "json-note";
  public static final String CRLF = "\r\n";

  private static String isSize(int n) {
    return "is-size-" + n;
  }

  private static final ObjectMapper SHARED_OBJECT_MAPPER =
      JsonMapper.builder()
          .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  @Getter private final ObjectMapper objectMapper;
  private final Map<UUID, NotePlaceholderEntry> notePlaceholders;
  private final RbelHtmlRenderer rbelHtmlRenderer;

  /** We are rendering a large element that should not be rendered fully. */
  @Getter private final boolean inShortenedRenderingMode;

  private static final TigerTypedConfigurationKey<String> logoFilePath =
      new TigerTypedConfigurationKey<>(
          new TigerConfigurationKey("tiger", "lib", "rbelLogoFilePath"), String.class);

  public RbelHtmlRenderingToolkit(RbelHtmlRenderer rbelHtmlRenderer) {
    this(SHARED_OBJECT_MAPPER, new HashMap<>(), rbelHtmlRenderer, false);
  }

  public RbelHtmlRenderingToolkit withInShortenedRenderingMode(boolean inShortenedRenderingMode) {
    if (this.inShortenedRenderingMode == inShortenedRenderingMode) {
      return this;
    }
    return this.toBuilder().inShortenedRenderingMode(inShortenedRenderingMode).build();
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag icon(final String iconName) {
    return span().withClass("icon").with(i().withClass("fas " + iconName));
  }

  public static String prettyPrintXml(final RbelElement element) {
    try {
      final OutputFormat format = OutputFormat.createPrettyPrint();
      element.getCharset().map(Charset::name).ifPresent(format::setEncoding);
      final org.dom4j.Document document = DocumentHelper.parseText(element.getRawStringContent());
      final StringWriter sw = new StringWriter();
      final XMLWriter writer = new XMLWriter(sw, format);
      writer.write(document);
      return new String(
          sw.getBuffer().toString().getBytes(element.getElementCharset()), StandardCharsets.UTF_8);
    } catch (final Exception e) {
      try {
        return Jsoup.parse(element.getRawStringContent()).html();
      } catch (Exception e2) {
        log.debug("Exception while pretty-printing {}", element.getRawStringContent(), e2);
        return element.getRawStringContent();
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
    return div().withClass("tile is-ancestor pe-2");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag vertParentTitle() {
    return div().withClass("tile is-vertical is-parent pe-2");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag childBoxNotifTitle(final String addClasses) {
    return div().withClass("tile is-child box notification pe-2 " + addClasses);
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag t1ms(final String text) {
    return h1(text).withClass("font-monospace title");
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag t2(final String text) {
    return h2(text).withClass("title");
  }

  public DomContent renderValueAsTextArea(RbelElement body) {
    return Optional.ofNullable(body)
        .filter(b -> !isInShortenedRenderingMode())
        .flatMap(b -> b.seekValue(String.class))
        .filter(s -> shouldRenderEntitiesWithSize(s.length()))
        .map(s -> s.split(CRLF))
        .map(Arrays::stream)
        .map(lines -> lines.map(line -> text(line + CRLF)).toList())
        .map(textarea().withClass("full-width")::with)
        .map(DomContent.class::cast)
        .orElseGet(() -> computeReplacementString(body));
  }

  private static DomContent computeReplacementString(RbelElement bodyElement) {
    return Optional.ofNullable(bodyElement)
        .map(
            body ->
                (DomContent)
                    span(RbelHtmlRenderer.buildOversizeReplacementString(body))
                        .withClass(isSize(7)))
        .orElseGet(TagCreator::pre);
  }

  public DomContent constructMessageId(final RbelElement message) {
    if (message.getParentNode() != null) {
      return span();
    }
    return span(getElementSequenceNumber(message))
        .withClass(
            "msg-sequence tag is-info is-light me-3 "
                + isSize(4)
                + " test-message-number "
                + message
                    .getFacet(RbelMessageInfoFacet.class)
                    .map(RbelMessageInfoFacet::getColor)
                    .orElse(""));
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public ContainerTag convert(final RbelElement element) {
    if (element.getParentNode() == null) {
      notePlaceholders.clear();
    }
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
    if (!shouldRenderEntitiesWithSize(element.getSize())) {
      return rbelHtmlRenderer
          .convert(element, key, this.withInShortenedRenderingMode(true))
          .orElseGet(
              () ->
                  addNotes(
                      element,
                      span(RbelHtmlRenderer.buildOversizeReplacementString(element))
                          .withClass(isSize(7))));
    }

    return convertUnforced(element, key)
        .orElseGet(
            () -> {
              if (element.hasFacet(RbelBinaryFacet.class)) {
                return addNotes(element, printAsBinary(element));
              } else {
                return addNotes(
                    element, span(performElementToTextConversion(element)).withClass(isSize(7)));
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
    return rbelHtmlRenderer.convert(element, key, this.withInShortenedRenderingMode(false));
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
        .withClass(" col is-one-fifth menu " + isSize(4) + " sidebar")
        .with(h2("Flow").withClass("mb-3 ms-2"), div().withClass("ms-1").withId("sidebar-menu"));
  }

  public String menuTab(final RbelElement rbelElement) {
    final String metaData = new JSONObject(MessageMetaDataDto.createFrom(rbelElement)).toString();
    return "createMenuEntry(" + metaData + ")";
  }

  private String getElementSequenceNumber(RbelElement rbelElement) {
    return rbelElement
        .getSequenceNumber()
        .map(zeroBased -> zeroBased + 1)
        .map(Object::toString)
        .orElse("0");
  }

  public void renderDocument(List<RbelElement> elements, boolean localRessources, Writer writer)
      throws IOException {
    writer.write("<!DOCTYPE html>\n");
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
                            : "https://code.jquery.com/jquery-4.0.0.js"),
                script()
                    .withSrc(
                        localRessources
                            ? "../webjars/bootstrap/js/bootstrap.bundle.min.js"
                            : "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"),
                script()
                    .withSrc(
                        localRessources
                            ? "../webjars/highlightjs/highlight.min.js"
                            : "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.10.0/build/highlight.min.js"),
                script()
                    .withSrc(
                        localRessources
                            ? "../webjars/highlightjs/languages/xml.min.js"
                            : "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.10.0/build/languages/xml.min.js"),
                script()
                    .withSrc(
                        localRessources
                            ? "../webjars/dayjs/dayjs.min.js"
                            : "https://cdnjs.cloudflare.com/ajax/libs/dayjs/1.11.9/dayjs.min.js"),
                link2CSS(
                    localRessources
                        ? "../webjars/bootstrap/css/bootstrap.min.css"
                        : "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css"),
                link2CSS(
                    localRessources
                        ? "../webjars/highlightjs/styles/stackoverflow-dark.min.css"
                        : "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.10.0/build/styles/stackoverflow-dark.min.css"),
                link2CSS(
                    localRessources
                        ? "../webjars/font-awesome/css/all.min.css"
                        : "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/7.0.1/css/all.min.css"),
                link().withRel("icon").withType("image/png").withHref(getLogoBase64Str()),
                tag("style")
                    .withId("rbel_css")
                    .with(
                        new UnescapedText(
                            IOUtils.resourceToString("/rbel.css", StandardCharsets.UTF_8)))),
            body()
                .withStyle("overflow-x: hidden;")
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
                                        .withClass("col " + isSize(6))
                                        .with(
                                            div()
                                                .withClass("row my-auto")
                                                .with(
                                                    div(rbelHtmlRenderer.getTitle())
                                                        .withClass(
                                                            "col navbar-title "
                                                                + isSize(3)
                                                                + " h-100"
                                                                + " my-auto"),
                                                    span(rbelHtmlRenderer.getVersionInfo())
                                                        .withClass(
                                                            "col-2 "
                                                                + isSize(7)
                                                                + " navbar-version")),
                                            div(
                                                new UnescapedText(
                                                    rbelHtmlRenderer.getSubTitle()))))),
                    section()
                        .withClass("row is-fullheight mainsection")
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
                                            "created fst-italic " + isSize(6) + " float-end me-6"),
                                    div()
                                        .withClass("rbel-main-content")
                                        .with(elements.stream().map(this::convertMessage).toList()),
                                    div("Created "
                                            + DateTimeFormatter.RFC_1123_DATE_TIME.format(
                                                ZonedDateTime.now()))
                                        .withClass(
                                            "created fst-italic "
                                                + isSize(6)
                                                + " float-end me-6")))))
        .with(
            script()
                .with(
                    new UnescapedText(IOUtils.resourceToString("/rbel.js", StandardCharsets.UTF_8)))
                .attr("id", "mainWebUiScript"),
            script(elements.stream().map(this::menuTab).collect(Collectors.joining("\n"))))
        .render(FlatHtml.into(writer, Config.global()));
  }

  public String renderDocument(List<RbelElement> elements, boolean localRessources)
      throws IOException {
    StringWriter writer = new StringWriter();
    renderDocument(elements, localRessources, writer);
    return writer.toString();
  }

  private String getLogoBase64Str() {
    return logoFilePath
        .getValue()
        .filter(StringUtils::isNotEmpty)
        .map(
            unchecked(
                filePath -> {
                  final byte[] bytes = FileUtils.readFileToByteArray(new File(filePath));
                  return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                },
                e -> new RbelRenderingException("Could not load file", e)))
        .orElseGet(
            () -> {
              try {
                return IOUtils.resourceToString(
                    "tiger-monochrome-64.png.base64",
                    StandardCharsets.UTF_8,
                    getClass().getClassLoader());
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

  private void addNotePlaceholder(
      UUID uuid, String stringToMatch, String renderedKeyReplacement, RbelElement originalElement) {
    notePlaceholders.put(
        uuid,
        NotePlaceholderEntry.builder()
            .stringToMatch(stringToMatch)
            .renderedKeyReplacement(renderedKeyReplacement)
            .renderedValueReplacement(renderNoteValues(originalElement))
            .build());
  }

  private static String renderNoteValues(RbelElement element) {
    return span()
        .with(
            element.getNotes().stream()
                .map(note -> div(i(note.getValue())).withClass(note.getStyle().toCssClass()))
                .toList())
        .withClass(JSON_NOTE)
        .render();
  }

  private JsonNode shadeJsonArray(
      JsonNode input, Optional<String> key, RbelElement originalElement) {
    final ArrayNode output = objectMapper.createArrayNode();
    if (originalElement.hasFacet(RbelNoteFacet.class)) {
      final UUID uuid = UUID.randomUUID();
      addNotePlaceholder(uuid, "\"" + uuid + "\"", span().render(), originalElement);
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
      addNotePlaceholder(
          uuid,
          "\"note\" : \"" + uuid + "\"" + (input.isEmpty() ? "" : ","),
          span().render(),
          originalElement);
      output.put("note", uuid.toString());
    }
    final var childLookup = new HashMap<String, RbelElement>();
    for (Entry<String, JsonNode> element : input.properties()) {
      output.set(
          element.getKey(),
          shadeJson(
              element.getValue(),
              Optional.of(element.getKey()),
              childLookup.computeIfAbsent(
                  element.getKey(),
                  key ->
                      originalElement
                          .getFirst(key)
                          .orElseThrow(
                              () ->
                                  new RuntimeException(
                                      "Unable to find matching Element for key " + key)))));
    }
    return output;
  }

  private JsonNode shadeJsonPrimitive(
      JsonNode input, Optional<String> key, RbelElement originalElement) {
    final JsonNode jsonElement =
        rbelHtmlRenderer
            .getRbelValueShader()
            .shadeValue(input, key)
            .map(
                shadedValue ->
                    (JsonNode) new StringNode(StringEscapeUtils.escapeHtml4(shadedValue)))
            .orElse(input);

    if (!originalElement.getNotes().isEmpty()) {
      final UUID uuid = UUID.randomUUID();
      addNotePlaceholder(
          uuid, "\"" + uuid + "\"", span(jsonElement.toString()).render(), originalElement);
      return new StringNode(uuid.toString());
    } else {
      return jsonElement;
    }
  }

  public List<DomContent> convertNested(final RbelElement el) {
    return el.traverseNestedMembers()
        .filter(entry -> !entry.getFacets().isEmpty())
        .map(
            child ->
                Pair.of(
                    child,
                    rbelHtmlRenderer.isRenderNestedObjectsWithoutFacetRenderer()
                            || isInShortenedRenderingMode()
                        ? Optional.of(convert(child, Optional.empty()))
                        : convertUnforced(child, Optional.empty())))
        .filter(pair -> pair.getValue().isPresent())
        .map(
            pair ->
                generateSubsection(
                    pair.getKey().findNodePath(), pair.getKey(), pair.getValue().get()))
        .toList();
  }

  public DomContent generateSubsection(String title, RbelElement element, ContainerTag<?> content) {
    boolean showExpanded = showElementExpanded(element);
    DivTag titleDiv =
        div(h2(title).withClass("title").withStyle("word-break: break-word;"))
            .withClass("message-header msg-section-header full-width")
            .with(
                RbelMessageRenderer.showBodyToggleButton(
                    showExpanded, "msg-section-toggle", Optional.empty()))
            .with(showContentButtonAndDialog(element, this))
            .with(addNotes(element));
    DivTag bodyDiv =
        div(div(content.withClass("notification tile is-child box pe-2"))
                .withClass("notification tile is-parent pe-2"))
            .withClass("message-body msg-section-body px-0");
    return collapsibleCard(
        titleDiv,
        bodyDiv,
        "msg-card message msg-section is-ancestor notification is-warning my-6 py-3 px-3",
        "mx-3 mt-3",
        "msg-section-content " + (showExpanded ? "" : "d-none"));
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

  public boolean shouldRenderEntitiesWithSize(long length) {
    return rbelHtmlRenderer.getMaximumEntitySizeInBytes() >= length;
  }

  public static PTag buildScrollableTextbox(String text) {
    return p(new UnescapedText(
            text.replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").replace("\n", "</br>")))
        .withStyle("white-space: nowrap; overflow-x: auto; overflow-y: hidden;");
  }

  public static Iterable<DomContent> collapsibleBox(String title, DomContent content) {
    String id = "collapse" + RandomStringUtils.insecure().nextAlphabetic(20); // NOSONAR
    return List.of(
        p(
            button(title)
                .withClass("btn btn-secondary")
                .attr("data-bs-target", "#" + id)
                .attr("data-bs-toggle", "collapse")
                .attr("role", "button")
                .attr("aria-expanded", "false")
                .attr("aria-controls", id)),
        div(div(content).withClass("card card-body")).withClass("collapse").withId(id));
  }

  public boolean showElementExpanded(RbelElement element) {
    return element.getDepth() <= rbelHtmlRenderer.getMaximumDefaultExpandedMessageDepth();
  }

  @Builder
  @AllArgsConstructor
  @Getter
  public static class NotePlaceholderEntry {

    private final String stringToMatch;
    private final String renderedKeyReplacement;
    private final String renderedValueReplacement;
  }

  private static final Pattern UUID_PATTERN =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  /**
   * Replaces all note-placeholder UUIDs in the formatted content string in a single forward pass.
   * Uses a UUID regex to find placeholders, looks up each UUID in the notePlaceholders map, and
   * replaces the full surrounding stringToMatch context. Collects all segments first, then
   * assembles them in a single pre-sized StringBuilder. Clears {@link #notePlaceholders} after
   * processing.
   */
  public String replaceNoteTags(String formattedContent) {
    if (notePlaceholders.isEmpty()) {
      return formattedContent;
    }

    var matcher = UUID_PATTERN.matcher(formattedContent);
    int lastEnd = 0;

    List<String> segments = new ArrayList<>();
    int totalLength = 0;

    while (matcher.find()) {
      UUID uuid;
      try {
        uuid = UUID.fromString(matcher.group());
      } catch (IllegalArgumentException e) {
        continue;
      }
      NotePlaceholderEntry entry = notePlaceholders.remove(uuid);
      if (entry == null) {
        continue;
      }

      int uuidStart = matcher.start();
      int matchStart = formattedContent.lastIndexOf(entry.getStringToMatch(), uuidStart);
      if (matchStart < 0 || matchStart < lastEnd) {
        continue;
      }

      totalLength +=
          addVerbatimSegment(
              matchStart > lastEnd, formattedContent.substring(lastEnd, matchStart), segments);

      int matchEnd = matchStart + entry.getStringToMatch().length();
      boolean hasTrailingComma =
          matchEnd < formattedContent.length() && formattedContent.charAt(matchEnd) == ',';
      if (hasTrailingComma) {
        matchEnd++;
      }
      totalLength += addReplacement(hasTrailingComma, entry, segments);
      lastEnd = matchEnd;
    }

    if (segments.isEmpty()) {
      return formattedContent;
    }

    // Trailing verbatim segment
    totalLength +=
        addVerbatimSegment(
            lastEnd < formattedContent.length(), formattedContent.substring(lastEnd), segments);

    // Assemble in one pre-sized StringBuilder
    var result = new StringBuilder(totalLength);
    for (String segment : segments) {
      result.append(segment);
    }
    return result.toString();
  }

  private static int addReplacement(
      boolean hasTrailingComma, NotePlaceholderEntry entry, List<String> segments) {
    String separator = hasTrailingComma ? "," : "";
    String replacement =
        entry.getRenderedKeyReplacement() + separator + entry.getRenderedValueReplacement();

    // Replacement segment
    segments.add(replacement);
    return replacement.length();
  }

  private static int addVerbatimSegment(boolean add, String verbatim, List<String> segments) {
    if (add) {
      segments.add(verbatim);
      return verbatim.length();
    }
    return 0;
  }
}
