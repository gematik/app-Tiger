/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.renderer;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.collapsibleCard;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.*;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class RbelMessageRenderer implements RbelHtmlFacetRenderer {

    public static ContainerTag buildAddressInfo(final RbelElement element) {
        if (!element.hasFacet(RbelTcpIpMessageFacet.class)) {
            return span();
        }
        final RbelTcpIpMessageFacet messageFacet = element.getFacetOrFail(RbelTcpIpMessageFacet.class);
        if (messageFacet.getSender().getFacet(RbelHostnameFacet.class).isEmpty() &&
            messageFacet.getReceiver().getFacet(RbelHostnameFacet.class).isEmpty()) {
            return span();
        }
        final String left;
        final String right;
        final String icon;
        final Optional<Boolean> isRequest = determineIsRequest(element);
        if (isRequest.isEmpty() || isRequest.get()) {
            left = messageFacet.getSender().getFacet(RbelHostnameFacet.class).map(RbelHostnameFacet::toString)
                .orElse(null);
            right = messageFacet.getReceiver().getFacet(RbelHostnameFacet.class).map(RbelHostnameFacet::toString)
                .orElse(null);
            icon = "fa-arrow-right";
        } else {
            left = messageFacet.getReceiver().getFacet(RbelHostnameFacet.class).map(RbelHostnameFacet::toString)
                .orElse(null);
            right = messageFacet.getSender().getFacet(RbelHostnameFacet.class).map(RbelHostnameFacet::toString)
                .orElse(null);
            icon = "fa-arrow-left";
        }

        return span()
            .withText(left == null ? "" : left)
            .with(icon(icon))
            .with(text(right == null ? "" : right))
            .withClass("is-size-7 ms-4");
    }

    private static Optional<Boolean> determineIsRequest(RbelElement element) {
        if (element.hasFacet(RbelRequestFacet.class)) {
            return Optional.of(true);
        } else if (element.hasFacet(RbelResponseFacet.class)) {
            return Optional.of(false);
        } else {
            return Optional.empty();
        }
    }

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
    public ContainerTag performRendering(final RbelElement element, final Optional<String> key,
        final RbelHtmlRenderingToolkit renderingToolkit) {
        final Optional<RbelHttpMessageFacet> httpMessageFacet = element.getFacet(RbelHttpMessageFacet.class);
        final Optional<RbelHttpRequestFacet> httpRequestFacet = element.getFacet(RbelHttpRequestFacet.class);
        final Optional<RbelHttpResponseFacet> httpResponseFacet = element.getFacet(RbelHttpResponseFacet.class);
        final Optional<Boolean> isRequest = determineIsRequest(element);
        //////////////////////////////// TITLE (+path, response-code...) //////////////////////////////////
        List<DomContent> messageTitleElements = new ArrayList<>();
        messageTitleElements.add(a().attr("name", element.getUuid()));
        messageTitleElements.add(
            i().withClasses("fas fa-toggle-on toggle-icon is-pulled-right me-3 is-size-3 msg-toggle",
                httpRequestFacet.map(f -> "has-text-link").orElse("has-text-success")));
        messageTitleElements.add(showContentButtonAndDialog(element, renderingToolkit));
        messageTitleElements.add(
            h1(
                renderingToolkit.constructMessageId(element),
                getRequestOrReplySymbol(isRequest),
                httpRequestFacet.map(f ->
                    span().with(
                            span(" " + f.getMethod().getRawStringContent() + " " + f.getPathAsString())
                                .withClass("is-family-monospace title is-size-6 ms-3")
                                .withTitle(f.getPathAsString())
                                .with(addNotes(f.getPath())))
                        .withClass("has-text-link text-ellipsis")).orElse(span()),
                httpResponseFacet.map(response ->
                    span(response.getResponseCode().getRawStringContent())
                        .withClass("is-family-monospace title ms-3")
                ).orElse(span("")),
                span().with(
                    buildTimingInfo(element), buildAddressInfo(element)
                ).withStyle(isRequest.map(r -> (isRequest.get() ? "display: block;" : "")).orElse(""))
            ).withClasses("title", "ms-3", "text-ellipsis", isRequest
                    .map(req -> req ? "has-text-link" : "has-text-success")
                    .orElse(""))
                .withStyle("overflow: hidden;"));
        messageTitleElements.addAll(addNotes(element));
        //////////////////////////////// HEADER & BODY //////////////////////////////////////
        List<DomContent> messageBodyElements = new ArrayList<>();
        if (httpMessageFacet.isPresent()) {
            messageBodyElements = performRenderingForBody(renderingToolkit, httpMessageFacet, httpRequestFacet);
        } else {
            // non parseable message
            messageBodyElements.add(renderingToolkit.convert(element));
        }
        return collapsibleCard(
            div()
                .with(messageTitleElements)
                .withClass("full-width"),
            ancestorTitle().with(messageBodyElements), "msg-card",
            "mx-3 " + isRequest.map(r -> Boolean.TRUE.equals(r) ? "mt-5" : "mt-2").orElse("mt-3"),
            "msg-content");
    }

    private List<DomContent> performRenderingForBody(RbelHtmlRenderingToolkit renderingToolkit,
        Optional<RbelHttpMessageFacet> httpMessageFacet,
        Optional<RbelHttpRequestFacet> httpRequestFacet) {

        List<DomContent> headerTitleElements = new ArrayList<>();
        headerTitleElements.add(
            i().withClasses("fas fa-toggle-on toggle-icon is-pulled-right me-3 is-size-3 has-text-primary header-toggle"));
        httpMessageFacet.map(
            a -> headerTitleElements.add(RbelHtmlRenderer.showContentButtonAndDialog(a.getHeader(), renderingToolkit)));
        headerTitleElements.add(div(httpRequestFacet.map(f -> t2("REQ Headers")).orElseGet(() -> t2("RES Headers")))
            .withClass("has-text-primary"));

        List<DomContent> bodyTitleElements = new ArrayList<>();
        bodyTitleElements.add(
            i().withClasses("fas fa-toggle-on toggle-icon is-pulled-right me-3 is-size-3 has-text-info body-toggle"));
        httpMessageFacet.map(
            a -> bodyTitleElements.add(RbelHtmlRenderer.showContentButtonAndDialog(a.getBody(), renderingToolkit)));
        bodyTitleElements.add(div(httpRequestFacet.map(f -> t2("REQ Body")).orElseGet(() -> t2("RES Body")))
            .withClass("has-text-info"));

        List<DomContent> messageBodyElements = new ArrayList<>();
        messageBodyElements.add(
            ancestorTitle().with(
                div().withClass("tile is-parent is-vertical pe-3").with(
                    div().with(collapsibleCard(
                        div().withClass("tile is-child pe-3")
                            .with(headerTitleElements),
                        httpMessageFacet.map(facet -> renderingToolkit.convert(facet.getHeader(), Optional.empty()))
                            .orElse(div()),
                        CLS_HEADER + " notification", "my-3","msg-header-content")),
                    httpMessageFacet.map(RbelHttpMessageFacet::getBody)
                        .map(RbelElement::getRawStringContent)
                        .map(s -> StringUtils.isBlank(s) ?
                            div("Empty body").withClass(CLS_BODY + " notification tile is-child") :
                            div().with(collapsibleCard(
                                div().withClass("tile is-child pe-3").with(bodyTitleElements),
                                renderingToolkit.convert(httpMessageFacet.get().getBody(), Optional.empty()),
                                CLS_BODY + " notification", "my-3", "msg-body-content")))
                        .orElse(div())
                )));

        return messageBodyElements;
    }

    private DomContent getRequestOrReplySymbol(Optional<Boolean> isRequestOptional) {
        return isRequestOptional
            .map(isRequest -> {
                if (isRequest) {
                    return i().withClass("fas fa-share me-3").withTitle("Request");
                } else {
                    return i().withClass("fas fa-reply me-3").withTitle("Response");
                }
            })
            .map(DomContent.class::cast)
            .orElse(span());
    }
}
