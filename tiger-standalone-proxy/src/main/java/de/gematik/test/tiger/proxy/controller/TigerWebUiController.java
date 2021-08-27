/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.GetMessagesAfterDto;
import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import j2html.tags.ContainerTag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

@Data
@RequiredArgsConstructor
@RestController
@RequestMapping("webui")
@Validated
@Slf4j
public class TigerWebUiController {

    private final TigerProxy tigerProxy;

    private final RbelHtmlRenderer renderer = new RbelHtmlRenderer();

    @GetMapping(value = "", produces = MediaType.TEXT_HTML_VALUE)
    public String getUI() throws IOException {
        String html = renderer.getEmptyPage()
            .replace("<div class=\"column ml-6\">", "<div class=\"column ml-6 msglist\">");
        String navbar = nav().withClass("navbar is-dark is-fixed-bottom").with(
            div().withClass("navbar-menu").with(
                div().withClass("navbar-start").with(
                    div().withClass("navbar-item").with(
                        button().withId("routeModalBtn")
                            .withClass("button is-dark modal-button")
                            .attr("data-target", "routeModalDialog").with(
                                div().withId("routeModalLed").withClass("led"),
                                span("Routes")
                            )
                    )
                ),
                div().withClass("navbar-end").with(
                    div().withClass("navbar-item").with(
                        button().withId("scrollLockBtn").withClass("button is-dark").with(
                            div().withId("scrollLockLed").withClass("led"),
                            span("Scroll Lock")
                        )
                    ),
                    div().withClass("navbar-item").with(
                        div().withId("updateLed").withClass("led "),
                        radio("1s", "updates", "update1", "1", "updates"),
                        radio("2s", "updates", "update2", "2", "updates"),
                        radio("5s", "updates", "update5", "5", "updates"),
                        radio("Manual", "updates", "noupdate", "0", "updates", true),
                        button("Update").withId("updateBtn").withClass("button is-outlined is-success")
                    ),
                    div().withClass("navbar-item").with(
                        span("Proxy port "),
                        b("" + tigerProxy.getPort()).withClass("ml-3")
                    )
                )
            )
        ).render();
        String routeModalHtml = IOUtils
            .toString(getClass().getResourceAsStream("/routeModal.html"), StandardCharsets.UTF_8);
        return html.replace("<div id=\"navbardiv\"></div>", navbar + routeModalHtml);
    }

    @GetMapping(value = "/getMsgAfter", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetMessagesAfterDto getMessagesAfter(
        @RequestParam(name = "lastMsgUuid", required = false) final String lastMsgUuid,
        @RequestParam(name = "maxMsgs", required = false) final Integer maxMsgs) {
        log.debug("requesting messages since " + lastMsgUuid + " (max. " + maxMsgs + ")");

        List<RbelElement> msgs = tigerProxy.getRbelLogger().getMessageHistory();
        int start = lastMsgUuid == null || lastMsgUuid.isBlank() ?
            -1 :
            (int) msgs.stream()
                .map(RbelElement::getUuid)
                .takeWhile(uuid -> !uuid.equals(lastMsgUuid))
                .count();
        // -1 as we get the uuid of the last response
        int end = msgs.size();
        if (maxMsgs != null && maxMsgs > 0 && end - start > maxMsgs) {
            end = start + 1 + maxMsgs;
        }
        var result = new GetMessagesAfterDto();
        result.setLastMsgUuid(lastMsgUuid);
        if (start < msgs.size()) {
            log.info("returning msgs > " + start + " of total " + msgs.size());
            List<RbelElement> retMsgs = msgs.subList(start + 1, end);
            result.setHtmlMsgList(retMsgs.stream()
                .map(msg -> new RbelHtmlRenderingToolkit(renderer)
                    .convert(msg, Optional.empty()).render())
                .collect(Collectors.toList()));
            result.setMetaMsgList(retMsgs.stream()
                .map(this::getMetaData)
                .collect(Collectors.toList()));
        } else {
            result.setHtmlMsgList(List.of());
            result.setMetaMsgList(List.of());
        }
        return result;
    }

    private MessageMetaDataDto getMetaData(RbelElement el) {
        MessageMetaDataDto.MessageMetaDataDtoBuilder b = MessageMetaDataDto.builder();
        b = b.uuid(el.getUuid())
            .headers(extractHeadersFromMessage(el))
            .sequenceNumber(0);
        if (el.hasFacet(RbelHttpRequestFacet.class)) {
            RbelHttpRequestFacet req = el.getFacetOrFail(RbelHttpRequestFacet.class);
            b = b.path(req.getPath().getRawStringContent())
                .method(req.getMethod().getRawStringContent())
                .recipient(el.getFacet(RbelTcpIpMessageFacet.class)
                    .map(RbelTcpIpMessageFacet::getReceiver)
                    .filter(Objects::nonNull)
                    .flatMap(element -> element.seekValue(RbelHostname.class))
                    .map(RbelHostname::toString)
                    .map(Object::toString)
                    .orElse(""));
        } else if (el.hasFacet(RbelHttpResponseFacet.class)) {
            b.status(el.getFacetOrFail(RbelHttpResponseFacet.class)
                    .getResponseCode().seekValue(Integer.class)
                    .orElse(-1))
                .sender(el.getFacet(RbelTcpIpMessageFacet.class)
                    .map(RbelTcpIpMessageFacet::getSender)
                    .filter(Objects::nonNull)
                    .flatMap(element -> element.seekValue(RbelHostname.class))
                    .map(RbelHostname::toString)
                    .map(Object::toString)
                    .orElse(""));
        } else {
            throw new IllegalArgumentException(
                "We do not support meta data for non http elements (" + el.getClass().getName() + ")");
        }
        return b.build();
    }

    private List<String> extractHeadersFromMessage(RbelElement el) {
        return el.getFacet(RbelHttpMessageFacet.class)
            .map(RbelHttpMessageFacet::getHeader)
            .flatMap(e -> e.getFacet(RbelHttpHeaderFacet.class))
            .map(header -> header.entrySet())
            .stream()
            .flatMap(Set::stream)
            .map(e -> (e.getKey() + "=" + e.getValue().getRawStringContent()))
            .collect(Collectors.toList());
    }


    private ContainerTag radio(final String text, final String name, final String id, String value,
                               final String clazz) {
        return radio(text, name, id, value, clazz, false);
    }

    private ContainerTag radio(final String text, final String name, final String id, String value, final String clazz,
                               boolean checked) {
        return div().withClass("radio-item").with(
            input().withType("radio").withName(name).withId(id).withValue(value).withClass(clazz)
                .attr("checked", checked),
            label(text).attr("for", id)
        );
    }
}
