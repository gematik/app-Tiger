/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import static j2html.TagCreator.*;
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
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClientException;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import de.gematik.test.tiger.proxy.configuration.TigerProxyReportConfiguration;
import de.gematik.test.tiger.proxy.data.GetMessagesAfterDto;
import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import de.gematik.test.tiger.proxy.data.ResetMessagesDto;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import j2html.tags.ContainerTag;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Data
@RequiredArgsConstructor
@RestController
@RequestMapping("webui")
@Validated
@Slf4j
public class TigerWebUiController implements ApplicationContextAware {

    private final TigerProxy tigerProxy;
    private final RbelHtmlRenderer renderer = new RbelHtmlRenderer();
    private ApplicationContext applicationContext;
    private final ApplicationConfiguration applicationConfiguration;

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.applicationContext = appContext;
    }

    @GetMapping(value = "", produces = MediaType.TEXT_HTML_VALUE)
    public String getUI() throws IOException {
        String html = renderer.getEmptyPage()
            .replace("<div class=\"column ml-6\">", "<div class=\"column ml-6 msglist\">");

        if (applicationConfiguration.isLocalResources()) {
            html = html
                .replace("https://cdn.jsdelivr.net/npm/bulma@0.9.1/css/bulma.min.css", "/webui/css/bulma.min.css")
                .replace("https://jenil.github.io/bulmaswatch/simplex/bulmaswatch.min.css", "/webui/css/bulmaswatch.min.css")
                .replace("https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.2/css/all.min.css",
                    "/webui/css/all.min.css");
        }
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
                    ),
                    div().withClass("navbar-item").with(
                        button().withId("scrollLockBtn").withClass("button is-dark").with(
                            div().withId("scrollLockLed").withClass("led"),
                            span("Scroll Lock")
                        )
                    )
                ),
                div().withClass("navbar-end").with(
                    div().withClass("navbar-item mr-3").with(
                        div().withId("updateLed").withClass("led "),
                        radio("1s", "updates", "update1", "1", "updates"),
                        radio("2s", "updates", "update2", "2", "updates"),
                        radio("5s", "updates", "update5", "5", "updates"),
                        radio("Manual", "updates", "noupdate", "0", "updates", true),
                        button("Update").withId("updateBtn").withClass("button is-outlined is-success")
                    ),
                    div().withClass("navbar-item ml-3").with(
                        button().withId("resetMsgs").withClass("button is-outlined is-danger").with(
                            i().withClass("far fa-trash-alt"),
                            span("Reset").withClass("ml-2").withStyle("color:inherit;")
                        )
                    ),
                    div().withClass("navbar-item").with(
                        button().withId("saveMsgs").withClass("button is-outlined is-success").with(
                            i().withClass("far fa-save"),
                            span("Save").withClass("ml-2").withStyle("color:inherit;")
                        )
                    ),
                    div().withClass("navbar-item").with(
                        button().withId("uploadMsgs").withClass("button is-outlined is-info").with(
                            i().withClass("fas fa-upload"),
                            span("Upload").withClass("ml-2").withStyle("color:inherit;")
                        )
                    ),
                    div().withClass("navbar-item").with(
                        span("Proxy port "),
                        b("" + tigerProxy.getPort()).withClass("ml-3")
                    ),
                    div().withClass("navbar-item").with(
                        button().withId("quitProxy").withClass("button is-outlined is-danger").with(
                            i().withClass("fas fa-power-off"),
                            span("Quit").withClass("ml-2").withStyle("color:inherit;")
                        )
                    )
                )
            )
        ).render();

        if (getClass().getResourceAsStream("/routeModal.html") == null) {
            throw new TigerProxyConfigurationException("Unable to locate route modal html template!");
        }
        String routeModalHtml = IOUtils
            .toString(getClass().getResourceAsStream("/routeModal.html"), StandardCharsets.UTF_8);

        if (applicationConfiguration.getReport() == null) {
            applicationConfiguration.setReport(new TigerProxyReportConfiguration());
        }
        if (getClass().getResourceAsStream("/config.js") == null) {
            throw new TigerProxyConfigurationException("Unable to locate config js template!");
        }
        String configJSSnippetStr = IOUtils
            .toString(getClass().getResourceAsStream("/config.js"), StandardCharsets.UTF_8)
            .replace("${ProxyPort}", String.valueOf(tigerProxy.getPort()))
            .replace("${FilenamePattern}", applicationConfiguration.getReport().getFilenamePattern())
            .replace("${UploadUrl}", applicationConfiguration.getReport().getUploadUrl());
        return html.replace("<div id=\"navbardiv\"></div>", navbar + routeModalHtml)
            .replace("</body>", configJSSnippetStr + "</body>");
    }

    @GetMapping(value = "/css/{cssfile}", produces = "text/css")
    public String getCSS(@PathVariable("cssfile") String cssFile) throws IOException {
        try(InputStream is = getClass().getResourceAsStream("/css/" + cssFile)) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "css file " + cssFile + " not found"
                );
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    @GetMapping(value = "/webfonts/{fontfile}", produces = "text/css")
    public ResponseEntity<byte[]> getWebFont(@PathVariable("fontfile") String fontFile) throws IOException {
        try(InputStream is = getClass().getResourceAsStream("/webfonts/" + fontFile)) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "webfont file " + fontFile + " not found"
                );
            }
            return new ResponseEntity<byte[]>(IOUtils.toByteArray(is), HttpStatus.OK);
        }
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
            log.debug("returning msgs > " + start + " of total " + msgs.size());
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

    @GetMapping(value = "/resetMsgs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResetMessagesDto resetMessages() {
        log.info("resetting currently recorded messages on rbel logger..");
        List<RbelElement> msgs = tigerProxy.getRbelLogger().getMessageHistory();
        ResetMessagesDto result = new ResetMessagesDto();
        result.setNumMsgs(msgs.size());
        msgs.clear();
        return result;
    }

    @GetMapping(value = "/quit", produces = MediaType.APPLICATION_JSON_VALUE)
    public void quitProxy() {
        log.info("shutting down tiger standalone proxy at port " + tigerProxy.getPort() + "...");
        ((ConfigurableApplicationContext) applicationContext).close();
        System.exit(0);
    }

    @PostMapping(value = "/uploadReport", produces = MediaType.APPLICATION_JSON_VALUE)
    public void uploadReport(@RequestBody String htmlReport) {
        if (applicationConfiguration.getReport() == null || applicationConfiguration.getReport().getUploadUrl().equals("UNDEFINED")) {
            throw new TigerProxyConfigurationException("Upload feature is not configured!");
        }
        log.info("uploading report...");
        performUploadReport(URLDecoder.decode(htmlReport, StandardCharsets.UTF_8));
    }

    private MessageMetaDataDto getMetaData(RbelElement el) {
        MessageMetaDataDto.MessageMetaDataDtoBuilder b = MessageMetaDataDto.builder();
        b = b.uuid(el.getUuid())
            .headers(extractHeadersFromMessage(el))
            .sequenceNumber(getElementSequenceNumber(el));
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

    private long getElementSequenceNumber(RbelElement rbelElement) {
        return rbelElement.getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getSequenceNumber)
            .orElse(0l);
    }

    private List<String> extractHeadersFromMessage(RbelElement el) {
        return el.getFacet(RbelHttpMessageFacet.class)
            .map(RbelHttpMessageFacet::getHeader)
            .flatMap(e -> e.getFacet(RbelHttpHeaderFacet.class))
            .map(RbelHttpHeaderFacet::entrySet)
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

    private void performUploadReport(String htmlReport) {
        // Connect to the web server endpoint
        String filename = applicationConfiguration.getReport().getFilenamePattern()
            .replace("${DATE}", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .replace("${TIME}", LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS")));
        String uploadUrl = applicationConfiguration.getReport().getUploadUrl() + filename;

        try {
            URL serverUrl = new URL(uploadUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) serverUrl.openConnection();
            String boundaryString = "----TigerProxyReport";
            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.addRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundaryString);
            if (applicationConfiguration.getReport().getUsername() != null) {
                String auth = applicationConfiguration.getReport().getUsername() + ":"
                    + applicationConfiguration.getReport().getPassword();
                byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
                String authHeaderValue = "Basic " + new String(encodedAuth);
                urlConnection.setRequestProperty("Authorization", authHeaderValue);
            }
            try (OutputStream outputStreamToRequestBody = urlConnection.getOutputStream();
                BufferedWriter httpRequestBodyWriter =
                    new BufferedWriter(new OutputStreamWriter(outputStreamToRequestBody))) {

                httpRequestBodyWriter.write("\n--" + boundaryString + "\n");
                httpRequestBodyWriter.write("Content-Disposition: form-data;"
                    + "name=\"Tiger proxy report archive\";"
                    + "filename=\"" + filename + "\""
                    + "\nContent-Type: application/zip\n\n");
                httpRequestBodyWriter.flush();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                    ZipEntry entry = new ZipEntry(filename);
                    zos.putNextEntry(entry);
                    zos.write(htmlReport.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    entry = new ZipEntry("application.cfg");
                    zos.putNextEntry(entry);
                    zos.write(applicationConfiguration.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }

                outputStreamToRequestBody.write(baos.toByteArray());
                outputStreamToRequestBody.flush();
                baos.close();

                // Mark the end of the multipart http request
                httpRequestBodyWriter.write("\n--" + boundaryString + "--\n");
                httpRequestBodyWriter.flush();
            }
        } catch (ProtocolException | MalformedURLException e) {
            throw new TigerProxyConfigurationException("Invalid upload url '" + uploadUrl + "'", e);
        } catch (IOException e) {
            throw new TigerRemoteProxyClientException("Failed to upload report to '" + uploadUrl + "'", e);
        }
    }

}
