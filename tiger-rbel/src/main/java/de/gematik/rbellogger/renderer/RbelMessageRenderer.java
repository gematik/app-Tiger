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
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class RbelMessageRenderer implements RbelHtmlFacetRenderer {

  public static final DomContent RENDER_FULLY_BUTTON =
      span()
          .with(
              a().withTitle("Full Message")
                  .withClass(
                      "btn modal-button full-message-button float-end mx-2"
                          + " test-modal-full-render")
                  .with(span().withClass("icon is-small").with(i().withClass("fas fa-expand"))));

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag buildAddressInfo(final RbelElement element) {
    final var messageFacet = element.getFacet(RbelTcpIpMessageFacet.class);
    String senderHostname =
        messageFacet
            .map(RbelTcpIpMessageFacet::getSender)
            .flatMap(f -> f.getFacet(RbelHostnameFacet.class))
            .map(RbelHostnameFacet::toString)
            .orElse(null);
    String receiverHostname =
        messageFacet
            .map(RbelTcpIpMessageFacet::getReceiver)
            .flatMap(f -> f.getFacet(RbelHostnameFacet.class))
            .map(RbelHostnameFacet::toString)
            .orElse(null);

    if (senderHostname == null && receiverHostname == null) {
      return span();
    }

    final String left;
    final String right;
    final String icon;
    if (isRequestMessage(element)) {
      left = senderHostname;
      right = receiverHostname;
      icon = "fa-arrow-right";
    } else {
      left = receiverHostname;
      right = senderHostname;
      icon = "fa-arrow-left";
    }

    return span()
        .withText(left == null ? "" : left)
        .with(icon(icon))
        .with(text(right == null ? "" : right))
        .withClass("is-size-7 ms-4");
  }

  private static boolean isRequestMessage(RbelElement element) {
    return element.hasFacet(RbelRequestFacet.class) || !element.hasFacet(RbelResponseFacet.class);
  }

  @SuppressWarnings({"rawtypes", "java:S3740"})
  public static ContainerTag buildTimingInfo(final RbelElement element) {
    if (!element.hasFacet(RbelMessageTimingFacet.class)) {
      return span();
    }
    final RbelMessageTimingFacet timingFacet = element.getFacetOrFail(RbelMessageTimingFacet.class);
    return span()
        .with(icon("fa-clock"))
        .withText(timingFacet.getTransmissionTime().format(DateTimeFormatter.ISO_TIME))
        .withClass("is-size-7 ms-4 ");
  }

  @Override
  public boolean checkForRendering(RbelElement element) {
    return element.hasFacet(RbelHttpMessageFacet.class)
        && element.getParentNode() != null; // prevent recursive call for non-http messages
  }

  @Override
  public ContainerTag performRendering(
      final RbelElement element,
      final Optional<String> key,
      final RbelHtmlRenderingToolkit renderingToolkit) {
    final Optional<RbelHttpMessageFacet> httpMessageFacet =
        element.getFacet(RbelHttpMessageFacet.class);
    final Optional<RbelHttpRequestFacet> httpRequestFacet =
        element.getFacet(RbelHttpRequestFacet.class);
    final Optional<RbelMessageInfoFacet> messageInfoFacet =
        element.getFacet(RbelMessageInfoFacet.class);
    final Optional<RbelElement> partnerMessage = findPartner(element);
    boolean showExpanded = renderingToolkit.showElementExpanded(element);
    ///////////////////// TITLE (+path, response-code...) //////////////////////////
    List<DomContent> messageTitleElements = new ArrayList<>();
    messageTitleElements.add(a().attr("name", element.getUuid()));
    messageTitleElements.add(showBodyToggleButton(showExpanded, "msg-toggle", messageInfoFacet));
    messageTitleElements.add(showContentButtonAndDialog(element, renderingToolkit));
    if (!renderingToolkit.shouldRenderEntitiesWithSize(element.getSize())) {
      messageTitleElements.add(RENDER_FULLY_BUTTON);
    }
    partnerMessage
        .map(RbelMessageRenderer::showPartnerMessageButton)
        .ifPresent(messageTitleElements::add);
    messageTitleElements.add(showMessageInfos(element, renderingToolkit, messageInfoFacet));
    messageTitleElements.addAll(addNotes(element));
    //////////////////////////////// HEADER & BODY //////////////////////////////////////
    List<DomContent> messageBodyElements = new ArrayList<>();
    if (httpMessageFacet.isPresent()) {
      messageBodyElements =
          performRenderingForBody(renderingToolkit, httpMessageFacet, httpRequestFacet);
    } else {
      // non parseable message
      messageBodyElements.add(
          div()
              .withClass("container")
              .with(ancestorTitle().with(renderingToolkit.convert(element))));
    }
    return collapsibleCard(
        div().with(messageTitleElements).withClass("full-width"),
        ancestorTitle().with(messageBodyElements),
        "msg-card",
        "mx-3 mt-3",
        "msg-content " + (showExpanded ? "" : "d-none"));
  }

  private static DomContent showPartnerMessageButton(RbelElement msg) {
    return span()
        .with(
            a().withClass(
                    "btn modal-button modal-button-details float-end" + " partner-message-button")
                .attr(
                    "onclick",
                    "scrollToMessage('"
                        + msg.getUuid()
                        + "',"
                        + msg.getFacet(RbelTcpIpMessageFacet.class)
                            .map(RbelTcpIpMessageFacet::getSequenceNumber)
                            .map(Object::toString)
                            .orElse("null")
                        + ")")
                .with(span().withClass("icon is-small").with(i().withClass("fas fa-right-left"))));
  }

  private DomContent showMessageInfos(
      RbelElement element,
      RbelHtmlRenderingToolkit renderingToolkit,
      Optional<RbelMessageInfoFacet> messageInfoFacet) {
    return h1(
            renderingToolkit.constructMessageId(element),
            constructMessageSymbol(element),
            messageInfoFacet
                .map(
                    facet ->
                        span(facet.getMenuInfoString()).withClass("font-monospace title ms-3 "))
                .orElse(span("")),
            span()
                .with(buildTimingInfo(element), buildAddressInfo(element))
                .withStyle("display: block;"))
        .withClasses(
            "title",
            "ms-3",
            "text-ellipsis",
            messageInfoFacet.map(RbelMessageInfoFacet::getColor).orElse(""))
        .withStyle("overflow: hidden;");
  }

  public static DomContent showBodyToggleButton(
      boolean showExpanded, String toggleClass, Optional<RbelMessageInfoFacet> messageInfoFacet) {
    return i().withClasses(
            "fa-solid toggle-icon float-end me-3 is-size-3 ms-auto",
            toggleClass,
            messageInfoFacet.map(RbelMessageInfoFacet::getColor).orElse(""),
            showExpanded ? "fa-toggle-on" : "fa-toggle-off");
  }

  private Optional<RbelElement> findPartner(RbelElement element) {
    return element
        .getFacet(TracingMessagePairFacet.class)
        .flatMap(msg -> msg.getOtherMessage(element));
  }

  private List<DomContent> performRenderingForBody(
      RbelHtmlRenderingToolkit renderingToolkit,
      Optional<RbelHttpMessageFacet> httpMessageFacet,
      Optional<RbelHttpRequestFacet> httpRequestFacet) {

    List<DomContent> headerTitleElements = new ArrayList<>();
    headerTitleElements.add(
        i().withClasses(
                "fa-solid fa-toggle-on toggle-icon float-end me-3 is-size-3 text-danger"
                    + " header-toggle"));
    httpMessageFacet.ifPresent(
        facet ->
            headerTitleElements.add(
                RbelHtmlRenderer.showContentButtonAndDialog(facet.getHeader(), renderingToolkit)));
    headerTitleElements.add(
        div(httpRequestFacet.map(f -> t2("REQ Headers")).orElseGet(() -> t2("RES Headers")))
            .withClass("text-danger"));

    List<DomContent> bodyTitleElements = new ArrayList<>();
    bodyTitleElements.add(
        i().withClasses(
                "fa-solid fa-toggle-on toggle-icon float-end me-3 is-size-3 text-info"
                    + " body-toggle"));
    httpMessageFacet.ifPresent(
        facet ->
            bodyTitleElements.add(
                RbelHtmlRenderer.showContentButtonAndDialog(facet.getBody(), renderingToolkit)));

    String bodyTitleString = httpRequestFacet.map(f -> "REQ Body").orElse("RES Body");

    bodyTitleElements.add(div(t2(bodyTitleString)).withClass("text-info"));

    List<DomContent> messageBodyElements = new ArrayList<>();
    messageBodyElements.add(
        ancestorTitle()
            .with(
                div()
                    .withClass("tile is-parent is-vertical pe-3")
                    .with(
                        div()
                            .with(
                                collapsibleCard(
                                    div().withClass("tile is-child pe-3").with(headerTitleElements),
                                    httpMessageFacet
                                        .map(
                                            facet ->
                                                renderingToolkit.convert(
                                                    facet.getHeader(), Optional.empty()))
                                        .orElse(div()),
                                    CLS_HEADER + " notification",
                                    "my-3",
                                    "msg-header-content test-msg-header-content")),
                        httpMessageFacet
                            .map(RbelHttpMessageFacet::getBody)
                            .map(RbelElement::getRawStringContent)
                            .map(
                                s ->
                                    StringUtils.isBlank(s)
                                        ? div()
                                            .withClass(
                                                CLS_BODY + " notification container is-child")
                                            .with(t2(bodyTitleString + " Empty"))
                                        : div()
                                            .with(
                                                collapsibleCard(
                                                    div()
                                                        .withClass("tile is-child pe-3")
                                                        .with(bodyTitleElements),
                                                    renderingToolkit.convert(
                                                        httpMessageFacet.get().getBody(),
                                                        Optional.empty()),
                                                    CLS_BODY + " notification",
                                                    "my-3",
                                                    "msg-body-content test-msg-body-content")))
                            .orElse(div()))));

    return messageBodyElements;
  }

  private DomContent constructMessageSymbol(RbelElement message) {
    return message
        .getFacet(RbelMessageInfoFacet.class)
        .map(
            f ->
                (ContainerTag)
                    i().withClass("fas me-1 " + f.getSymbol() + " " + f.getColor())
                        .withTitle(f.getTitle()))
        .orElse(span());
  }
}
