/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.proxy.controller;

import static j2html.TagCreator.*;
import com.google.common.html.HtmlEscapers;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.rbellogger.util.RbelFileWriterUtils;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClientException;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import de.gematik.test.tiger.proxy.configuration.TigerProxyReportConfiguration;
import de.gematik.test.tiger.proxy.data.GetMessagesAfterDto;
import de.gematik.test.tiger.proxy.data.JexlQueryResponseDto;
import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import de.gematik.test.tiger.proxy.data.ResetMessagesDto;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyWebUiException;
import j2html.tags.ContainerTag;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
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
    private final ApplicationConfiguration applicationConfiguration;
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.applicationContext = appContext;
    }

    @GetMapping(value = "/trafficLog.tgr", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public String downloadTraffic() {
        return tigerProxy.getRbelMessages().stream()
            .map(RbelFileWriterUtils::convertToRbelFileString)
            .collect(Collectors.joining("\n\n"));
    }

    @GetMapping(value = "", produces = MediaType.TEXT_HTML_VALUE)
    public String getUI() throws IOException {
        String html = replaceScript(renderer.getEmptyPage()
            .replace("<div class=\"column ml-6\">", "<div class=\"column ml-6 msglist\">"));

        if (applicationConfiguration.isLocalResources()) {
            log.info("Running with local resources...");
            html = html
                .replace("https://cdn.jsdelivr.net/npm/bulma@0.9.1/css/bulma.min.css", "/webui/css/bulma.min.css")
                .replace("https://jenil.github.io/bulmaswatch/simplex/bulmaswatch.min.css",
                    "/webui/css/bulmaswatch.min.css")
                .replace("https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.2/css/all.min.css",
                    "/webui/css/all.min.css");
        }
        String navbar = nav().withClass("navbar is-dark is-fixed-bottom").with(
            div().withClass("navbar-menu").with(
                div().withClass("navbar-start").with(
                    div().withClass("navbar-item").with(
                        button().withId("routeModalBtn")
                            .withClass("button is-dark")
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
                    ),
                    form().attr("onSubmit", "return false;")
                        .with(
                        div().withClass("navbar-item").with(
                            div().withClass("field").with(
                                p().withClass("control has-icons-left").with(
                                    input().withClass("input is-rounded has-text-dark")
                                        .withType("text")
                                        .withPlaceholder("RbelPath filter criterion")
                                        .withId("setFilterCriterionInput")
                                        .attr("autocomplete", "on")
                                )
                            )
                        ),
                        div().withClass("navbar-item").with(
                            button().withId("setFilterCriterionBtn").withClass("button is-outlined is-success").with(
                                i().withClass("fas fa-filter"),
                                span("Set Filter").withClass("ml-2").withStyle("color:inherit;")
                            )
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

        if (applicationConfiguration.getReport() == null) {
            applicationConfiguration.setReport(new TigerProxyReportConfiguration());
        }
        String configJSSnippetStr = loadResourceToString("/configScript.html")
            .replace("${ProxyPort}", String.valueOf(tigerProxy.getPort()))
            .replace("${FilenamePattern}", applicationConfiguration.getReport().getFilenamePattern())
            .replace("${UploadUrl}", applicationConfiguration.getReport().getUploadUrl());
        return html.replace("<div id=\"navbardiv\"></div>", navbar +
                loadResourceToString("/routeModal.html") +
                loadResourceToString("/jexlModal.html") +
                loadResourceToString("/saveModal.html"))
            .replace("</body>", configJSSnippetStr + "</body>");
    }

    private String replaceScript(String replace) {
        var jsoup = Jsoup.parse(renderer.getEmptyPage()
            .replace("<div class=\"column ml-6\">", "<div class=\"column ml-6 msglist\">"));
        final Element script = jsoup.select("script").get(0);
        script.dataNodes().get(0).replaceWith(
            new DataNode(loadResourceToString("/tigerProxy.js")));
        return jsoup.html();
    }

    private String loadResourceToString(String resourceName) {
        final InputStream resource = getClass().getResourceAsStream(resourceName);
        if (resource == null) {
            throw new TigerProxyConfigurationException("Unable to load resource '" + resourceName + "' !");
        }
        try {
            return IOUtils.toString(resource, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TigerProxyWebUiException("Exception while loading resource '" + resourceName + "'", e);
        }
    }

    @GetMapping(value = "/css/{cssfile}", produces = "text/css")
    public String getCSS(@PathVariable("cssfile") String cssFile) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/css/" + cssFile)) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "css file " + cssFile + " not found"
                );
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    @GetMapping(value = "/testJexlQuery", produces = MediaType.APPLICATION_JSON_VALUE)
    public JexlQueryResponseDto testJexlQuery(
        @RequestParam(name = "msgUuid") final String msgUuid,
        @RequestParam(name = "query") final String query) {
        RbelJexlExecutor jexlExecutor = new RbelJexlExecutor();
        final RbelElement targetMessage = getTigerProxy().getRbelMessages().stream()
            .filter(msg -> msg.getUuid().equals(msgUuid))
            .findFirst().orElseThrow();
        final Map<String, Object> messageContext = jexlExecutor.buildJexlMapContext(targetMessage, Optional.empty());
        final RbelElementTreePrinter treePrinter = RbelElementTreePrinter.builder()
            .rootElement(targetMessage)
            .printFacets(false)
            .build();
        return JexlQueryResponseDto.builder()
            .matchSuccessful(jexlExecutor.matchesAsJexlExpression(targetMessage, query))
            .messageContext(messageContext)
            .rbelTreeHtml(HtmlEscapers.htmlEscaper().escape(treePrinter.execute())
                .replace(RbelAnsiColors.RESET.toString(),"</span>")
                .replace(RbelAnsiColors.RED_BOLD.toString(),"<span class='has-text-danger'>")
                .replace(RbelAnsiColors.CYAN.toString(),"<span class='has-text-info'>")
                .replace(RbelAnsiColors.YELLOW_BRIGHT.toString(),"<span class='has-text-primary has-text-weight-bold'>")
                .replace(RbelAnsiColors.GREEN.toString(),"<span class='has-text-warning'>")
                .replace(RbelAnsiColors.BLUE.toString(),"<span class='has-text-success'>")
                .replace("\n", "<br/>"))
            .build();
    }

    @GetMapping(value = "/webfonts/{fontfile}", produces = "text/css")
    public ResponseEntity<byte[]> getWebFont(@PathVariable("fontfile") String fontFile) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/webfonts/" + fontFile)) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "webfont file " + fontFile + " not found"
                );
            }
            return new ResponseEntity<>(IOUtils.toByteArray(is), HttpStatus.OK);
        }
    }

    @GetMapping(value = "/getMsgAfter", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetMessagesAfterDto getMessagesAfter(
        @RequestParam(name = "lastMsgUuid", required = false) final String lastMsgUuid,
        @RequestParam(name = "filterCriterion", required = false) final String filterCriterion) {
        log.debug("requesting messages since " + lastMsgUuid + " (filtered by . " + filterCriterion + ")");

        var jexlExecutor = new RbelJexlExecutor();

        List<RbelElement> msgs = tigerProxy.getRbelLogger().getMessageHistory().stream()
            .dropWhile(element -> {
                if (StringUtils.isEmpty(lastMsgUuid)) {
                    return false;
                } else {
                    return !element.getUuid().equals(lastMsgUuid);
                }
            })
            .filter(element -> {
                if (StringUtils.isEmpty(filterCriterion)) {
                    return true;
                }
                return jexlExecutor.matchesAsJexlExpression(element, filterCriterion, Optional.empty());
            })
            .collect(Collectors.toList());

        var result = new GetMessagesAfterDto();
        result.setLastMsgUuid(lastMsgUuid);
        log.debug("returning {} messages of total {}", msgs.size(), tigerProxy.getRbelMessages().size());
        result.setHtmlMsgList(msgs.stream()
            .map(msg -> new RbelHtmlRenderingToolkit(renderer)
                .convert(msg, Optional.empty()).render())
            .collect(Collectors.toList()));
        result.setMetaMsgList(msgs.stream()
            .map(this::getMetaData)
            .collect(Collectors.toList()));
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
    public void quitProxy(@RequestParam(name = "noSystemExit", required = false) final String noSystemExit) {
        log.info("shutting down tiger standalone proxy at port " + tigerProxy.getPort() + "...");
        tigerProxy.clearAllRoutes();
        tigerProxy.shutdown();
        log.info("shutting down tiger standalone proxy ui...");
        int exitCode = SpringApplication.exit(applicationContext);
        if (exitCode != 0) {
            log.warn("Exit of tiger proxy ui not successful - exit code: " + exitCode);
        }
        if (StringUtils.isEmpty(noSystemExit)) {
            System.exit(0);
        }
    }

    @PostMapping(value = "/uploadReport", produces = MediaType.APPLICATION_JSON_VALUE)
    public void uploadReport(@RequestBody String htmlReport) {
        if (applicationConfiguration.getReport() == null || applicationConfiguration.getReport().getUploadUrl()
            .equals("UNDEFINED")) {
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