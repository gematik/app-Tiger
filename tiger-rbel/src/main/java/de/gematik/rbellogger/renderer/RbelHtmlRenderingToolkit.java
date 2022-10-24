/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.renderer;

import com.google.gson.*;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.*;
import j2html.TagCreator;
import j2html.tags.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static j2html.TagCreator.*;

@RequiredArgsConstructor
@Slf4j
public class RbelHtmlRenderingToolkit {

    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();
    public static final String CLS_HEADER = "is-primary";
    public static final String CLS_BODY = "is-info";
    public static final String CLS_PKIOK = "is-success";
    public static final String CLS_PKINOK = "is-primary";
    private static final String HEX_STYLE = "display: inline-flex;padding-bottom: 0.2rem;padding-top: 0.2rem;white-space: revert;";

    @Getter
    private final Map<UUID, JsonNoteEntry> noteTags = new HashMap<>();
    private final RbelHtmlRenderer rbelHtmlRenderer;

    public static ContainerTag icon(final String iconName) {
        return span().withClass("icon")
            .with(i().withClass("fas " + iconName));
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

    public static List<DomContent> addNotes(final RbelElement el, final String... extraClasses) {
        final String className = StringUtils.join(extraClasses, " ");
        return el.getNotes().stream()
            .map(note -> div(i().withText(note.getValue()))
                .withClass(
                    "is-family-primary has-text-weight-light m-3 " + className + " " + note.getStyle().toCssClass())
                .withStyle("word-break: normal;"))
            .collect(Collectors.toUnmodifiableList());
    }

    public static EmptyTag link2CSS(final String url) {
        return link().attr("rel", "stylesheet")
            .withHref(url);
    }

    public static ContainerTag ancestorTitle() {
        return div().withClass("tile is-ancestor pr-3");
    }

    public static ContainerTag vertParentTitle() {
        return div().withClass("tile is-vertical is-parent pr-3");
    }

    public static ContainerTag childBoxNotifTitle(final String addClasses) {
        return div().withClass("tile is-child box notification pr-3 " + addClasses);
    }

    public static ContainerTag t1ms(final String text) {
        return h1(text).withClass("is-family-monospace title");
    }

    public static ContainerTag t2(final String text) {
        return h2(text).withClass("title");
    }

    public DomContent constructMessageId(final RbelElement message) {
        if (message.getParentNode() != null) {
            return span();
        }
        return span(getElementSequenceNumber(message))
            .withClass("msg-sequence tag is-info is-light mr-3 is-size-2");
    }

    public ContainerTag convert(final RbelElement element) {
        return convert(element, Optional.empty());
    }

    public ContainerTag convert(final RbelElement element, final Optional<String> key) {
        return convertUnforced(element, key)
            .orElseGet(() -> {
                if (shouldRenderEntitiesWithSize(element.getRawContent().length)) {
                    if (element.hasFacet(RbelBinaryFacet.class)) {
                        return printAsBinary(element);
                    } else {
                        return span(performElementToTextConversion(element));
                    }
                } else {
                    return span(RbelHtmlRenderer.buildOversizeReplacementString(element));
                }
            });
    }

    private String performElementToTextConversion(final RbelElement el) {
        return rbelHtmlRenderer.getRbelValueShader().shadeValue(el, el.findKeyInParentElement())
            .or(() -> Optional.ofNullable(el)
                .map(RbelElement::getRawStringContent)
                .filter(Objects::nonNull)
                .map(str -> str.replace("\n", "<br/>")))
            .orElse("");
    }

    public Optional<ContainerTag> convertUnforced(final RbelElement element, final Optional<String> key) {
        return rbelHtmlRenderer.convert(element, key, this);
    }

    public Optional<String> shadeValue(RbelElement element, Optional<String> key) {
        return rbelHtmlRenderer.getRbelValueShader().shadeValue(element, key);
    }

    public ContainerTag printAsBinary(final RbelElement el) {
        return
            div(
                pre().withStyle(HEX_STYLE).withText("Offset    "
                    + " | " + " 0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f"
                    + " | " + "ASCII Text      ")
            )
                .with(
                    IntStream.range(0, (el.getRawContent().length + 15) / 16)
                        .mapToObj(line ->
                            div(
                                pre().withStyle(HEX_STYLE)
                                    .withText(StringUtils.leftPad(Integer.toHexString(line * 16), 8, '0') + "  "
                                        + " | " + getLineAsHexString(el.getRawContent(), line)
                                        + " | " + getLineAsAsciiString(el.getRawContent(), line))
                            )
                        )
                        .collect(Collectors.toList())
                );
    }

    private String getLineAsHexString(byte[] rawContent, int start) {
        return StringUtils.rightPad(IntStream.range(start * 16, Math.min((start + 1) * 16, rawContent.length))
                .mapToObj(index -> Hex.toHexString(rawContent, index, 1))
                .collect(Collectors.joining(" ")),
            16 * 3 - 1, ' ');
    }

    private String getLineAsAsciiString(byte[] rawContent, int start) {
        return StringUtils.rightPad(IntStream.range(start * 16, Math.min((start + 1) * 16, rawContent.length))
                .mapToObj(index -> rawContent[index])
                .filter(Objects::nonNull)
                .map(bit -> new String(new byte[]{bit}, StandardCharsets.US_ASCII)
                    .replaceAll("[^ -~]", "."))
                .collect(Collectors.joining("")),
            16, ' ');
    }

    public DomContent renderMenu(final List<RbelElement> elements) {
        return div().withClass(" column is-one-fifth menu is-size-4 sidebar").with(
            a(i().withClass("fas fa-angle-double-up")).withId("collapse-all").withHref("#")
                .withClass("is-pulled-right mr-3"),
            a(i().withClass("fas fa-angle-double-down")).withId("expand-all").withHref("#")
                .withClass("is-pulled-right mr-3"),
            h2("Flow").withClass("mb-4 ml-2"),
            div().withClass("ml-5").withId("sidebar-menu").with(
                elements.stream()
                    .map(this::menuTab)
                    .collect(Collectors.toList())
            )
        );
    }

    public DomContent menuTab(final RbelElement rbelElement) {
        final String uuid = rbelElement.getUuid();
        if (rbelElement.hasFacet(RbelRequestFacet.class)) {
            return div().withClass("ml-5").with(
                a().with(
                    div(span(getElementSequenceNumber(rbelElement)).withClass("tag is-info is-light mr-1"),
                        i().withClass("fas fa-share"),
                        text(" REQUEST")).withClass("menu-label mb-1 has-text-link"),
                    div(rbelElement.getFacetOrFail(RbelRequestFacet.class).getMenuInfoString())
                        .attr("style", "text-overflow: ellipsis;overflow: hidden;")
                        .withClass("is-size-6 ml-3")
                ).withHref("#" + uuid).withClass("mt-3 is-block")
            );
        } else {
            return a(span(getElementSequenceNumber(rbelElement)).withClass("tag is-info is-light mr-1"),
                i().withClass("fas fa-reply"),
                text(" RESPONSE"),
                div(rbelElement.getFacet(RbelResponseFacet.class).map(RbelResponseFacet::getMenuInfoString).orElse(""))
                    .attr("style", "text-overflow: ellipsis;overflow: hidden;")
                    .withClass("is-size-6 ml-3"))
                .withHref("#" + uuid).withClass("menu-label ml-5 mt-3 is-block has-text-success");
        }
    }

    private String getElementSequenceNumber(RbelElement rbelElement) {
        return rbelElement.getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getSequenceNumber)
            .map(zeroBased -> zeroBased + 1)
            .map(Object::toString)
            .orElse("0");
    }

    public String renderDocument(List<RbelElement> elements) throws IOException {
        return TagCreator.document(
            html(
                head(
                    meta().attr("charset", "utf-8"),
                    meta().attr("name", "viewport").attr("content", "width=device-width, initial-scale=1"),
                    title().withText("Rbel Flow"),
                    script().withSrc("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"),
                    script().withSrc("https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"),
                    link2CSS("https://cdn.jsdelivr.net/npm/bulma@0.9.1/css/bulma.min.css"),
                    link2CSS("https://jenil.github.io/bulmaswatch/simplex/bulmaswatch.min.css"),
                    link2CSS("https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.2/css/all.min.css"),
                    link().withRel("icon").withType("image/png").withHref(
                        "data:image/png;base64,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"),
                    tag("style").with(
                        new UnescapedText(IOUtils.resourceToString("/rbel.css", StandardCharsets.UTF_8)))
                ),
                body().withClass("has-navbar-fixed-bottom")
                    .with(
                        div().withId("navbardiv"),
                        section().withClass("main-content").with(
                            section().withClass("columns is-vcentered header").with(
                                div().withClass("column is-one-fifth is-inline-block logo").with(
                                    img().withSrc( // RBelLogger icon 320px size
                                        "data:image/png;base64,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")
                                ),
                                div().withClass("is-inline-block").with(
                                        h1(rbelHtmlRenderer.getTitle()).withClass("is-size-1 mb-3"),
                                        div(new UnescapedText(rbelHtmlRenderer.getSubTitle())))
                                    .withClass("is-size-6 is-italic is-clearfix")
                            )
                        ),
                        section().withClass("columns is-fullheight").with(
                            renderMenu(elements),
                            div().withClass("column ml-6").with(
                                div("Created " +
                                    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()))
                                    .withClass("created is-italic is-size-6 is-pulled-right mr-6"),
                                div().with(
                                    elements.stream()
                                        .filter(el -> el.hasFacet(RbelTcpIpMessageFacet.class))
                                        .map(this::convertMessage)
                                        .collect(Collectors.toList())
                                ),
                                div("Created " +
                                    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()))
                                    .withClass("created is-italic is-size-6 is-pulled-right mr-6")
                            )
                        )
                    )
            ).with(
                script()
                    .with(new UnescapedText(IOUtils.resourceToString("/rbel.js", StandardCharsets.UTF_8)))
            )
        );
    }

    public DomContent convertMessage(RbelElement element) {
        return div(new RbelMessageRenderer()
            .performRendering(element, Optional.empty(), this));
    }

    public JsonElement shadeJson(final JsonElement input, final Optional<String> key,
                                 final RbelElement originalElement) {
        if (input.isJsonPrimitive()) {
            final JsonElement jsonElement = rbelHtmlRenderer.getRbelValueShader().shadeValue(input, key)
                .map(shadedValue -> (JsonElement) new JsonPrimitive(StringEscapeUtils.escapeHtml4(shadedValue)))
                .orElse(input);

            if (!originalElement.getNotes().isEmpty()) {
                final UUID uuid = UUID.randomUUID();
                noteTags.put(uuid, JsonNoteEntry.builder()
                    .stringToMatch("\"" + uuid + "\"")
                    .tagForKeyReplacement(span(jsonElement.toString()))
                    .tagForValueReplacement(span().with(originalElement.getNotes().stream()
                            .map(note -> div(i(note.getValue())).withClass(note.getStyle().toCssClass()))
                            .collect(Collectors.toList()))
                        .withClass("json-note"))
                    .build());
                return new JsonPrimitive(uuid.toString());
            } else {
                return jsonElement;
            }
        } else if (input.isJsonObject()) {
            final JsonObject output = new JsonObject();
            if (originalElement.hasFacet(RbelNoteFacet.class)) {
                final UUID uuid = UUID.randomUUID();

                noteTags.put(uuid, JsonNoteEntry.builder()
                    .stringToMatch("\"note\": \"" + uuid + "\""
                        + (input.getAsJsonObject().size() == 0 ? "" : ","))
                    .tagForKeyReplacement(span())
                    .tagForValueReplacement(span().with(originalElement.getNotes().stream()
                            .map(note -> div(i(note.getValue())).withClass(note.getStyle().toCssClass()))
                            .collect(Collectors.toList()))
                        .withClass("json-note"))
                    .build());
                output.addProperty("note", uuid.toString());
            }
            for (final Entry<String, JsonElement> element : input.getAsJsonObject().entrySet()) {
                output.add(element.getKey(), shadeJson(element.getValue(), Optional.of(element.getKey()),
                    originalElement.getFirst(element.getKey())
                        .orElseThrow(
                            () -> new RuntimeException("Unable to find matching Element for key " + element.getKey()))
                ));
            }
            return output;
        } else if (input.isJsonArray()) {
            final JsonArray output = new JsonArray();
            if (originalElement.hasFacet(RbelNoteFacet.class)) {
                final UUID uuid = UUID.randomUUID();
                noteTags.put(uuid, JsonNoteEntry.builder()
                    .stringToMatch("\"" + uuid + "\"")
                    .tagForKeyReplacement(span())
                    .tagForValueReplacement(span().with(originalElement.getNotes().stream()
                            .map(note -> div(i(note.getValue())).withClass(note.getStyle().toCssClass()))
                            .collect(Collectors.toList()))
                        .withClass("json-note"))
                    .build());
                output.add(uuid.toString());
            }
            for (int i = 0; i < input.getAsJsonArray().size(); i++) {
                final int finalI = i;
                final List<? extends RbelElement> rbelListElements = originalElement
                    .getFacetOrFail(RbelListFacet.class).getChildNodes();
                output.add(shadeJson(input.getAsJsonArray().get(i), key
                    .map(v -> v + "." + finalI), rbelListElements.get(i)));
            }
            return output;
        } else if (input.isJsonNull()) {
            return input;
        } else {
            throw new RuntimeException("Unshadeable JSON-Type " + input.getClass().getSimpleName());
        }
    }

    public List<ContainerTag> convertNested(final RbelElement el) {
        return el.traverseAndReturnNestedMembers().stream()
            .filter(entry -> !entry.getFacets().isEmpty())
            .map(child -> Pair.of(child,
                rbelHtmlRenderer.isRenderNestedObjectsWithoutFacetRenderer() ?
                    Optional.of(convert(child, Optional.empty())) :
                    convertUnforced(child, Optional.empty())))
            .filter(pair -> pair.getValue().isPresent())
            .map(pair ->
                article().withClass("message is-ancestor notification is-warning my-6 py-3 px-3")
                    .with(
                        div(h2(pair.getKey().findNodePath())
                            .withClass("title").withStyle("word-break: keep-all;"))
                            .withClass("message-header")
                            .with(addNotes(pair.getKey()))
                            .with(showContentButtonAndDialog(pair.getKey(), this)),
                        div(div(pair.getValue().get()
                            .withClass("notification tile is-child box pr-3")
                        ).withClass("notification tile is-parent pr-3"))
                            .withClass("message-body px-0")
                    )
            )
            .collect(Collectors.toList());
    }

    public List<DomContent> packAsInfoLine(String parameterName, DomContent... contentObject) {
        return List.of(div().withClass("columns is-multiline is-mobile").with(
            div().withClass("column is-one-quarter")
                .with(b().withText(parameterName + ": ")),
            div().withClass("column")
                .with(contentObject)));
    }


    public DomContent formatHex(RbelElement value) {
        return span().withText(Hex.toHexString(value.getRawContent()))
            .withStyle("font-family: monospace; padding-right: 0.3rem;");
    }

    public DomContent formatHexAlike(String value) {
        return span().withText(value)
            .withStyle("font-family: monospace; padding-right: 0.3rem;");
    }

    public boolean shouldRenderEntitiesWithSize(int length) {
        return rbelHtmlRenderer.getMaximumEntitySizeInBytes() > length;
    }

    @Builder
    @AllArgsConstructor
    @Getter
    public static class JsonNoteEntry {

        private final String stringToMatch;
        private final Tag tagForKeyReplacement;
        private final Tag tagForValueReplacement;
    }
}
