package de.gematik.test.tiger.proxy.controller;

import static j2html.TagCreator.*;
import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.rbellogger.data.elements.RbelHttpMessage;
import de.gematik.rbellogger.data.elements.RbelHttpRequest;
import de.gematik.rbellogger.data.elements.RbelHttpResponse;
import de.gematik.rbellogger.data.elements.RbelMultiValuedMapElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.GetMessagesAfterDto;
import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import j2html.tags.ContainerTag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

@Data
@RequiredArgsConstructor
@RestController
@RequestMapping("webui")
@Validated
@Slf4j
public class TigerWebUiController {

    private final TigerProxy tigerProxy;

    private final RbelHtmlRenderer renderer = new RbelHtmlRenderer();

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String getUI() throws IOException {
        String html = renderer.getEmptyPage();
        String navbar = nav().withClass("navbar is-dark is-fixed-bottom").with(
            div().withClass("navbar-menu").with(
                div().withClass("navbar-start").with(
                    div().withClass("navbar-item").with(
                        div().withId("routeModalLed").withClass("led"),
                        button("Routes").withId("routeModalBtn")
                            .withClass("button is-outlined is-success modal-button")
                            .attr("data-target", "routeModalDialog")
                    )
                ),
                div().withClass("navbar-end").with(
                    div().withClass("navbar-item").with(
                        button("Unlocked").withId("scrollLockBtn").withClass("button is-outlined is-info")
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

        List<RbelMessage> msgs = tigerProxy.getRbelLogger().getMessageHistory();
        int start = lastMsgUuid == null || lastMsgUuid.isBlank() ?
            -1 :
            (int) msgs.stream()
                .map(RbelMessage::getHttpMessage)
                .map(RbelHttpMessage::getUuid)
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
            List<RbelMessage> retMsgs = msgs.subList(start + 1, end);
            result.setHtmlMsgList(retMsgs.stream()
                .map(msg -> renderer.convert(msg.getHttpMessage(), Optional.empty()).render())
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


    private MessageMetaDataDto getMetaData(RbelMessage el) {
        MessageMetaDataDto.MessageMetaDataDtoBuilder b = MessageMetaDataDto.builder();
        b = b
            .uuid(el.getHttpMessage().getUuid())
            .headers(convertMultiValue2List(el.getHttpMessage().getHeader()))
            .sequenceNumber(el.getSequenceNumber());
        if (el.getHttpMessage() instanceof RbelHttpRequest) {
            RbelHttpRequest req = (RbelHttpRequest) el.getHttpMessage();
            b = b.path(req.getPath().getContent()).method(req.getMethod()).recipient(el.getRecipient().getHostname());
        } else if (el.getHttpMessage() instanceof RbelHttpResponse) {
            RbelHttpResponse res = (RbelHttpResponse) el.getHttpMessage();
            b.status(res.getResponseCode()).sender(el.getSender().getHostname());
        } else {
            throw new IllegalArgumentException(
                "We do not support meta data for non http elements (" + el.getClass().getName() + ")");
        }
        return b.build();
    }

    private List<String> convertMultiValue2List(RbelMultiValuedMapElement el) {
        return el.entrySet().stream()
            .map(e -> (e.getKey() + "=" + e.getValue()))
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
