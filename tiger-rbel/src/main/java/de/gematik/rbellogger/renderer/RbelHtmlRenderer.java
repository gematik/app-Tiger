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

import static j2html.TagCreator.*;

import de.gematik.rbellogger.converter.RbelValueShader;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelAsn1Facet;
import de.gematik.rbellogger.util.BinaryClassifier;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.util.*;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
@Getter
public class RbelHtmlRenderer {

  private static final List<RbelHtmlFacetRenderer> htmlRenderer = new ArrayList<>();
  public static final String OVERSIZE_REPLACEMENT_TEXT_PRE = "<...redacted due to size of ";
  public static final String OVERSIZE_REPLACEMENT_TEXT_POST = " Mb...>";
  public static final String MODAL = "modal";
  private final RbelValueShader rbelValueShader;
  @Setter private boolean renderAsn1Objects = false;
  @Setter private boolean renderNestedObjectsWithoutFacetRenderer = false;
  @Setter private int maximumEntitySizeInBytes = 4 * 1024 * 1024;
  @Setter private String title = "Tiger Proxy Log";
  @Setter private String subTitle = "";
  @Setter private String versionInfo = "";

  public RbelHtmlRenderer(final RbelValueShader rbelValueShader) {
    this.rbelValueShader = rbelValueShader;
  }

  public RbelHtmlRenderer() {
    rbelValueShader = new RbelValueShader();
  }

  public static String render(final Collection<RbelElement> elements) {
    return render(elements, new RbelValueShader());
  }

  public static String render(
      final Collection<RbelElement> elements, final RbelValueShader valueShader) {
    return new RbelHtmlRenderer(valueShader).performRendering(elements, false);
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag collapsibleCard(
      final ContainerTag title,
      final ContainerTag body,
      String classes,
      String spaces,
      String contentClasses) {
    return div()
        .withClass("container page-break " + spaces)
        .with(
            div()
                .withClass("card full-width test-card " + classes)
                .with(
                    header()
                        .withClass("card-header test-card-header")
                        .with(
                            div()
                                .withClass("card-header-title test-card-header-title card-toggle")
                                .with(title)),
                    div()
                        .withClass("card-content test-card-content " + contentClasses)
                        .with(div().with(body))));
  }

  public static DomContent showContentButtonAndDialog(
      final RbelElement el, final RbelHtmlRenderingToolkit renderingToolkit) {
    final String id = "dialog" + RandomStringUtils.randomAlphanumeric(20); // NOSONAR
    return span()
        .with(
            a().withClass("btn modal-button modal-button-details float-end mx-3 test-modal-content")
                .attr("data-bs-target", "#" + id)
                .attr("data-bs-toggle", MODAL)
                .with(span().withClass("icon is-small").with(i().withClass("fas fa-align-left"))),
            div()
                .withClass(MODAL)
                .withId(id)
                .attr("role", "dialog")
                .with(
                    div()
                        .withClass("modal-dialog")
                        .with(
                            div().withClass("modal-background"),
                            div()
                                .withClass("modal-content")
                                .attr("role", "document")
                                .attr("style", "width: 900px;")
                                .with(
                                    div()
                                        .withClass("modal-header bg-dark")
                                        .with(
                                            div()
                                                .withStyle("display: inline-flex;")
                                                .with(
                                                    p("Raw content of " + el.findNodePath())
                                                        .withStyle(
                                                            "align-self: center;color:#ffff;")
                                                        .withClass("modal-title"),
                                                    button()
                                                        .withClass(
                                                            "btn btn-sm copyToClipboard-button")
                                                        .attr("data-target", "text-" + id)
                                                        .with(i().withClass("fa fa-clipboard"))),
                                            button()
                                                .withClass("btn btn-close btn-close-white")
                                                .attr("data-bs-dismiss", MODAL)
                                                .attr("aria-label", "Close")),
                                    article()
                                        .withClass("message")
                                        .with(
                                            div()
                                                .withClass("message-body")
                                                .with(
                                                    pre(printRawContentOfElement(
                                                            el, renderingToolkit))
                                                        .withId("text-" + id)
                                                        .withStyle(
                                                            "white-space: pre-wrap;word-wrap:"
                                                                + " break-word;")))))));
  }

  @Nullable
  private static String printRawContentOfElement(
      final RbelElement el, final RbelHtmlRenderingToolkit renderingToolkit) {
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
        + ((el.getRawContent().length / 10_000D) / 100.)
        + OVERSIZE_REPLACEMENT_TEXT_POST;
  }

  public static void registerFacetRenderer(RbelHtmlFacetRenderer rbelFacetRenderer) {
    htmlRenderer.add(rbelFacetRenderer);
  }

  public String doRender(final Collection<RbelElement> elements) {
    return performRendering(elements, false);
  }

  @SneakyThrows
  private String performRendering(final Collection<RbelElement> elements, boolean localRessources) {
    RbelHtmlRenderingToolkit renderingToolkit = new RbelHtmlRenderingToolkit(this);
    return renderingToolkit.renderDocument(new ArrayList<>(elements), localRessources);
  }

  @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "rawtypes", "java:S3740"})
  public Optional<ContainerTag> convert(
      final RbelElement element,
      final Optional<String> key,
      final RbelHtmlRenderingToolkit renderingToolkit) {
    if (element.getFacets().isEmpty() && ArrayUtils.isEmpty(element.getRawContent())) {
      return Optional.empty();
    }
    final List<ContainerTag> renderedFacets =
        htmlRenderer.stream()
            .filter(
                renderer ->
                    renderAsn1Objects
                        || !(renderer
                            .getClass()
                            .getName()
                            .startsWith(RbelAsn1Facet.class.getName())))
            .filter(renderer -> renderer.checkForRendering(element))
            .sorted(Comparator.comparing(RbelHtmlFacetRenderer::order))
            .map(renderer -> renderer.performRendering(element, key, renderingToolkit))
            .toList();
    if (renderedFacets.isEmpty()) {
      return Optional.empty();
    } else if (renderedFacets.size() == 1) {
      return Optional.of(renderedFacets.get(0));
    } else {
      return Optional.of(div().with(renderedFacets));
    }
  }

  public String getEmptyPage(boolean localRessources) {
    return performRendering(List.of(), localRessources);
  }
}
