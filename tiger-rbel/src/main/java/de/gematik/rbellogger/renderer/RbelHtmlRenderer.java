/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.renderer;

import de.gematik.rbellogger.converter.RbelValueShader;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelAsn1Facet;
import de.gematik.rbellogger.util.BinaryClassifier;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

@Slf4j
@Getter
public class RbelHtmlRenderer {

    private static final List<RbelHtmlFacetRenderer> htmlRenderer = new ArrayList<>();
    public static final String OVERSIZE_REPLACEMENT_TEXT_PRE = "<...redacted due to size of ";
    public static final String OVERSIZE_REPLACEMENT_TEXT_POST = " Mb...>";
    private final RbelValueShader rbelValueShader;
    @Setter
    private boolean renderAsn1Objects = false;
    @Setter
    private boolean renderNestedObjectsWithoutFacetRenderer = false;
    @Setter
    private int maximumEntitySizeInBytes = 4 * 1024 * 1024;
    @Setter
    private String title = "RBelLogger";
    @Setter
    private String subTitle = "<p>The [R]everse [B]ridle [E]vent [L]ogger pays tribute to the fact "
        + "that many agile projects' specifications, alas somewhat complete, "
        + "lack specificality. Using PoCs most of the time does not resolve this as the code is not "
        + "well enough documented and communication between nodes is not observable or "
        + "logged in a well enough readable manner.</p> "
        + "<p>This is where the RBeL Logger comes into play.</p> "
        + "<p>Attaching it to a network, RestAssured or Wiremock interface or instructing it to read from a recorded PCAP file, "
        + "produces this shiny communication log supporting Plain HTTP, JSON, JWT and even JWE!</p>";

    public RbelHtmlRenderer(final RbelValueShader rbelValueShader) {
        this.rbelValueShader = rbelValueShader;
    }

    public RbelHtmlRenderer() {
        rbelValueShader = new RbelValueShader();
    }

    public static String render(final List<RbelElement> elements) {
        return render(elements, new RbelValueShader());
    }

    public static String render(final List<RbelElement> elements, final RbelValueShader valueShader) {
        return new RbelHtmlRenderer(valueShader)
            .performRendering(elements);
    }

    public static ContainerTag collapsibleCard(final ContainerTag title, final ContainerTag body, String classes, String spaces) {
        return
            div().withClass("container page-break " + spaces).with(
                div().withClass("card full-width " + classes)
                    .with(
                        header().withClass("card-header")
                            .with(
                                div().withClass("card-header-title card-toggle")
                                    .with(title)
                            ),
                        div().withClass("card-content")
                            .with(
                                div().with(body)
                            )
                    )
            );
    }

    public static DomContent showContentButtonAndDialog(final RbelElement el,
                                                        final RbelHtmlRenderingToolkit renderingToolkit) {
        final String id = "dialog" + RandomStringUtils.randomAlphanumeric(20);//NOSONAR
        return span().with(
            a().withClass("button modal-button modal-button-details is-pulled-right mx-3")
                .attr("data-target", id)
                .with(span().withClass("icon is-small").with(
                    i().withClass("fas fa-align-left")
                )),
            div().withClass("modal")
                .withId(id)
                .with(
                    div().withClass("modal-background"),
                    div().withClass("modal-content").with(
                        article().withClass("message").with(
                            div().withClass("message-header").with(
                                div().withStyle("display: inline-flex;").with(p("Raw content of " + el.findNodePath()).withStyle("align-self: center;"),
                                button().withClass("copyToClipboard-button").attr("data-target", "text-" + id).with(
                                    i().withClass("fa fa-clipboard")
                                )),
                                button().withClass("delete").attr("aria-label", "delete")
                            ),
                            div().withClass("message-body")
                                .with(pre(printRawContentOfElement(el, renderingToolkit)).withId("text-"+id)
                                    .withStyle("white-space: pre-wrap;word-wrap: break-word;"))
                        )
                    ),
                    button().withClass("modal-close is-large")
                        .attr("aria-label", "close")
                )
        );
    }

    @Nullable
    private static String printRawContentOfElement(final RbelElement el,
                                                   final RbelHtmlRenderingToolkit renderingToolkit) {
        if (renderingToolkit.shouldRenderEntitiesWithSize(el.getRawContent().length)) {
            if (BinaryClassifier.isBinary(el.getRawContent())) {
                return Hex.toHexString(el.getRawContent());
            } else {
                return el.getRawStringContent();
            }
        } else {
            return buildOversizeReplacementString(el);
        }
    }

    public static String buildOversizeReplacementString(RbelElement el) {
        return OVERSIZE_REPLACEMENT_TEXT_PRE
            + ((el.getRawContent().length / 10_000) / 100.)
            + OVERSIZE_REPLACEMENT_TEXT_POST;
    }

    public static void registerFacetRenderer(RbelHtmlFacetRenderer rbelFacetRenderer) {
        htmlRenderer.add(rbelFacetRenderer);
    }

    public String doRender(final List<RbelElement> elements) {
        return performRendering(elements);
    }

    @SneakyThrows
    private String performRendering(final List<RbelElement> elements) {
        RbelHtmlRenderingToolkit renderingToolkit = new RbelHtmlRenderingToolkit(this);

        return renderingToolkit.renderDocument(new ArrayList(elements));
    }

    public Optional<ContainerTag> convert(final RbelElement element, final Optional<String> key,
                                          final RbelHtmlRenderingToolkit renderingToolkit) {
        if (element.getFacets().isEmpty() && ArrayUtils.isEmpty(element.getRawContent())) {
            return Optional.empty();
        }
        final List<ContainerTag> renderedFacets = htmlRenderer.stream()
            .filter(renderer -> renderAsn1Objects || !(renderer.getClass().getName().startsWith(RbelAsn1Facet.class.getName())))
            .filter(renderer -> renderer.checkForRendering(element))
            .sorted(Comparator.comparing(RbelHtmlFacetRenderer::order))
            .map(renderer -> renderer.performRendering(element, key, renderingToolkit))
            .collect(Collectors.toList());
        if (renderedFacets.isEmpty()) {
            return Optional.empty();
        } else if (renderedFacets.size() == 1) {
            return Optional.of(renderedFacets.get(0));
        } else {
            return Optional.of(
                div().with(renderedFacets));
        }
    }

    public String getEmptyPage() {
        return performRendering(List.of());
    }
}
