/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.controller;

import static j2html.TagCreator.*;
import com.google.common.html.HtmlEscapers;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.rbellogger.util.RbelFileWriterUtils;
import de.gematik.test.tiger.common.config.TigerProperties;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClientException;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import de.gematik.test.tiger.proxy.data.*;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletResponse;
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
    private final AtomicBoolean versionToBeAdded = new AtomicBoolean(false);
    private boolean versionAdded = false;

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.applicationContext = appContext;
    }

    @GetMapping(value = "/trafficLog*.tgr", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public String downloadTraffic(
        @RequestParam(name = "lastMsgUuid", required = false) final String lastMsgUuid,
        @RequestParam(name = "pageSize", required = false) final Optional<Integer> pageSize,
        HttpServletResponse response) {
        int actualPageSize = pageSize
            .orElse(getApplicationConfiguration().getMaximumTrafficDownloadPageSize());
        final ArrayList<RbelElement> filteredMessages = new ArrayList<>(messsages()).stream()
            .dropWhile(msg -> {
                if (StringUtils.isEmpty(lastMsgUuid)) {
                    return false;
                } else {
                    return !msg.getUuid().equals(lastMsgUuid);
                }
            })
            .filter(msg -> !msg.getUuid().equals(lastMsgUuid))
            .collect(Collectors.toCollection(ArrayList::new));
        final int returnedMessages = Math.min(filteredMessages.size(), actualPageSize);
        response.addHeader("available-messages", String.valueOf(filteredMessages.size()));
        response.addHeader("returned-messages", String.valueOf(returnedMessages));

        final String result = filteredMessages.stream()
            .limit(actualPageSize)
            .map(RbelFileWriterUtils::convertToRbelFileString)
            .collect(Collectors.joining("\n\n"));

        if (!result.isEmpty()) {
            response.addHeader("last-uuid", filteredMessages.get(returnedMessages - 1).getUuid());
        }
        return result;
    }

    @GetMapping(value = "", produces = MediaType.TEXT_HTML_VALUE)
    public String getUI(@RequestParam(defaultValue = "false") boolean embedded) {
        TigerProperties tigerProperties = new TigerProperties();
        synchronized (versionToBeAdded) {
            if (!versionAdded) {
                String versionHtml =
                    "<div class=\"is-size-6\" style=\"text-align: right;margin-bottom: 1rem!important;margin-right: 1.5em;\">"
                        + tigerProperties.getFullBuildVersion() + "</div>";
                renderer.setSubTitle(versionHtml + renderer.getSubTitle());
                versionAdded = true;
            }
        }
        String html = renderer.getEmptyPage();
        // hide sidebar
        String targetDiv;
        if (embedded) {
            targetDiv = "<div class=\"column msglist embeddedlist\">";
        } else {
            targetDiv = "<div class=\"column ml-6 msglist\">";
        }
        html = replaceScript(html.replace("<div class=\"column ml-6\">", targetDiv));

        if (applicationConfiguration.isLocalResources()) {
            log.info("Running with local resources...");
            html = html
                .replace("https://cdn.jsdelivr.net/npm/bulma@0.9.1/css/bulma.min.css", "/webui/css/bulma.min.css")
                .replace("https://jenil.github.io/bulmaswatch/simplex/bulmaswatch.min.css",
                    "/webui/css/bulmaswatch.min.css")
                .replace("https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.2/css/all.min.css",
                    "/webui/css/all.min.css");
        }

        String navbar;

        if (embedded) {
            navbar = createNavbar(tigerProxy, "margin-bottom: 4em;", "margin-inline: auto;");
        } else {
            navbar = createNavbar(tigerProxy, "", "");
        }

        String configJSSnippetStr = loadResourceToString("/configScript.html")
            .replace("${ProxyPort}", String.valueOf(tigerProxy.getProxyPort()))
            .replace("${FilenamePattern}", applicationConfiguration.getFilenamePattern())
            .replace("${UploadUrl}", applicationConfiguration.getUploadUrl());
        return html.replace("<div id=\"navbardiv\"></div>", navbar +
                loadResourceToString("/routeModal.html") +
                loadResourceToString("/jexlModal.html") +
                loadResourceToString("/saveModal.html"))
            .replace("</body>", configJSSnippetStr + "</body>");
    }

    private String getNavbarItemNot4embedded() {
        return "navbar-item not4embedded";
    }

    private String navbarItem() {
        return "navbar-item";
    }

    private String darkButton() {
        return "button is-dark";
    }

    private String successOutlineButton() {
        return "button is-outlined is-success";
    }


    private String createNavbar(TigerProxy tigerProxy, String styleNavbar, String styleNavbarStart) {
        return nav().withClass("navbar is-dark is-fixed-bottom").withStyle(styleNavbar)
            .with(
                div().withClass("navbar-menu").with(
                    div().withClass("navbar-start").withStyle(styleNavbarStart).with(
                        div().withClass(getNavbarItemNot4embedded()).with(
                            button().withId("routeModalBtn").withClass(successOutlineButton())
                                .attr("data-target", "routeModalDialog").with(
                                    i().withClass("fas fa-exchange-alt"),
                                    span("Routes").withClass("ml-2").withStyle("color:inherit;")
                                )
                        ),
                        div().withClass(getNavbarItemNot4embedded()).with(
                            button().withId("scrollLockBtn").withClass(darkButton()).with(
                                div().withId("scrollLockLed").withClass("led"),
                                span("Scroll Lock")
                            )
                        ),
                        div().withClass(navbarItem()).with(
                            button().withId("collapsibleMessageDetailsBtn").withClass(darkButton()).with(
                                div().withId("collapsibleMessageDetails").withClass("led"),
                                span("Hide Details")
                            )
                        ),
                        form().withClass("is-inline-flex").attr("onSubmit", "return false;")
                            .with(
                                div().withClass(navbarItem()).with(
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
                                div().withClass("navbar-item dropdown is-up").with(
                                    div().withId("dropdown-filter-button").withClass("dropdown-trigger").with(
                                        button().withClass("button").with(
                                            span().withClass("icon is-small").with(
                                                i().withClass("fas fa-angle-up")
                                            )
                                        )
                                    ),
                                    div().withClass("dropdown-menu").withRole("menu").with(
                                        div().withClass("dropdown-content").with(
                                            div().withClass("dropdown-item nested dropdown").with(
                                                div().withClass("dropdown-trigger").with(
                                                    button("Request from  ").withClass("button")
                                                ),
                                                div().withClass("dropdown-menu").withRole("menu").with(
                                                    div().withId("requestFromContent").withClass("dropdown-content")
                                                )
                                            ),
                                            div().withClass("dropdown-item nested dropdown").with(
                                                div().withClass("dropdown-trigger").with(
                                                    button("Request to  ").withClass("button")
                                                ),
                                                div().withClass("dropdown-menu").withRole("menu").with(
                                                    div().withId("requestToContent").withClass("dropdown-content")
                                                )
                                            )
                                        )
                                    )
                                ),
                                div().withClass(navbarItem()).with(
                                    button().withId("setFilterCriterionBtn").withClass(successOutlineButton())
                                        .with(
                                            i().withClass("fas fa-filter"),
                                            span("Set Filter").withClass("ml-2").withStyle("color:inherit;")
                                        )
                                )
                            ),
                        div().withClass(getNavbarItemNot4embedded() + " mr-3").with(
                            div().withId("updateLed").withClass("led "),
                            radio("1s", "updates", "update1", "1", "updates"),
                            radio("2s", "updates", "update2", "2", "updates"),
                            radio("5s", "updates", "update5", "5", "updates"),
                            radio("Manual", "updates", "noupdate", "0", "updates"),
                            button("Update").withId("updateBtn").withClass(successOutlineButton())
                        ),
                        div().withClass(getNavbarItemNot4embedded() + " ml-3").with(
                            button().withId("resetMsgs").withClass("button is-outlined is-danger").with(
                                i().withClass("far fa-trash-alt"),
                                span("Reset").withClass("ml-2").withStyle("color:inherit;")
                            )
                        ),
                        div().withClass("navbar-item").with(
                            button().withId("saveMsgs").withClass(successOutlineButton()).with(
                                i().withClass("far fa-save"),
                                span("Save").withClass("ml-2").withStyle("color:inherit;")
                            )
                        ),
                        div().withClass(getNavbarItemNot4embedded()).with(
                            button().withId("importMsgs").withClass(successOutlineButton()).with(
                                i().withClass("far fa-folder-open"),
                                span("Import").withClass("ml-2").withStyle("color:inherit;")
                            )
                        ),
                        div().withClass(getNavbarItemNot4embedded()).with(
                            button().withId("uploadMsgs").withClass("button is-outlined is-info").with(
                                i().withClass("far fa-upload"),
                                span("Upload").withClass("ml-2").withStyle("color:inherit;")
                            )
                        ),
                        div().withClass(navbarItem()).with(
                            span("Proxy port "),
                            b("" + tigerProxy.getProxyPort()).withClass("ml-3")
                        ),
                        div().withClass(getNavbarItemNot4embedded()).with(
                            button().withId("quitProxy").withClass("button is-outlined is-danger").with(
                                i().withClass("fas fa-power-off"),
                                span("Quit").withClass("ml-2").withStyle("color:inherit;")
                            )
                        )
                    )
                )
            ).render();
    }

    private String replaceScript(String html) {
        var jsoup = Jsoup.parse(html);
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
        final RbelElement targetMessage = messsages().stream()
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
                .replace(RbelAnsiColors.RESET.toString(), "</span>")
                .replace(RbelAnsiColors.RED_BOLD.toString(), "<span class='has-text-danger'>")
                .replace(RbelAnsiColors.CYAN.toString(), "<span class='has-text-info'>")
                .replace(RbelAnsiColors.YELLOW_BRIGHT.toString(),
                    "<span class='has-text-primary has-text-weight-bold'>")
                .replace(RbelAnsiColors.GREEN.toString(), "<span class='has-text-warning'>")
                .replace(RbelAnsiColors.BLUE.toString(), "<span class='has-text-success'>")
                .replace("\n", "<br/>"))
            .build();
    }

    @GetMapping(value = "/testRbelExpression", produces = MediaType.APPLICATION_JSON_VALUE)
    public JexlQueryResponseDto testRbelExpression(
        @RequestParam(name = "msgUuid") final String msgUuid,
        @RequestParam(name = "rbelPath") final String rbelPath) {
        final List<RbelElement> targetElements = getTigerProxy().getRbelMessages().stream()
            .filter(msg -> msg.getUuid().equals(msgUuid))
            .map(msg -> msg.findRbelPathMembers(rbelPath))
            .flatMap(List::stream)
            .collect(Collectors.toList());
        if (targetElements.isEmpty()) {
            return JexlQueryResponseDto.builder()
                .build();
        }
        final RbelElementTreePrinter treePrinter = RbelElementTreePrinter.builder()
            .rootElement(targetElements.get(0))
            .printFacets(false)
            .build();
        return JexlQueryResponseDto.builder()
            .rbelTreeHtml(HtmlEscapers.htmlEscaper().escape(treePrinter.execute())
                .replace(RbelAnsiColors.RESET.toString(), "</span>")
                .replace(RbelAnsiColors.RED_BOLD.toString(), "<span class='has-text-danger jexlResponseLink' style='cursor: pointer;'>")
                .replace(RbelAnsiColors.CYAN.toString(), "<span class='has-text-info'>")
                .replace(RbelAnsiColors.YELLOW_BRIGHT.toString(),
                    "<span class='has-text-primary has-text-weight-bold'>")
                .replace(RbelAnsiColors.GREEN.toString(), "<span class='has-text-warning'>")
                .replace(RbelAnsiColors.BLUE.toString(), "<span class='has-text-success'>")
                .replace("\n", "<br/>"))
            .elements(targetElements.stream()
                .map(RbelElement::findNodePath)
                .map(key -> "$." + key)
                .collect(Collectors.toList()))
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

        List<RbelElement> msgs = messsages().stream()
            .dropWhile(msg -> {
                if (StringUtils.isEmpty(lastMsgUuid)) {
                    return false;
                } else {
                    return !msg.getUuid().equals(lastMsgUuid);
                }
            })
            .filter(msg -> !msg.getUuid().equals(lastMsgUuid))
            .filter(msg -> {
                if (StringUtils.isEmpty(filterCriterion)) {
                    return true;
                }
                return jexlExecutor.matchesAsJexlExpression(msg, filterCriterion, Optional.empty())
                    || jexlExecutor.matchesAsJexlExpression(findPartner(msg), filterCriterion, Optional.empty());
            })
            .collect(Collectors.toList());

        var result = new GetMessagesAfterDto();
        result.setLastMsgUuid(lastMsgUuid);
        log.debug("Returning {} messages of total {}", msgs.size(), tigerProxy.getRbelMessages().size());
        result.setHtmlMsgList(msgs.stream()
            .map(msg -> new RbelHtmlRenderingToolkit(renderer).convertMessage(msg).render())
            .collect(Collectors.toList()));
        result.setMetaMsgList(msgs.stream()
            .map(MessageMetaDataDto::createFrom)
            .collect(Collectors.toList()));
        return result;
    }

    private RbelElement findPartner(RbelElement msg) {
        return msg.getFacet(TracingMessagePairFacet.class)
            .map(pairFacet -> {
                if (pairFacet.getRequest() == msg) {
                    return pairFacet.getResponse();
                } else {
                    return pairFacet.getRequest();
                }
            })
            .orElse(null);
    }

    @GetMapping(value = "/resetMsgs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResetMessagesDto resetMessages() {
        log.info("Resetting currently recorded messages on rbel logger..");
        List<RbelElement> msgs = tigerProxy.getRbelLogger().getMessageHistory();
        ResetMessagesDto result = new ResetMessagesDto();
        result.setNumMsgs(msgs.size());
        msgs.clear();
        return result;
    }

    @GetMapping(value = "/quit", produces = MediaType.APPLICATION_JSON_VALUE)
    public void quitProxy(@RequestParam(name = "noSystemExit", required = false) final String noSystemExit) {
        log.info("Shutting down tiger standalone proxy at port " + tigerProxy.getProxyPort() + "...");
        tigerProxy.clearAllRoutes();
        tigerProxy.shutdown();
        log.info("Shutting down tiger standalone proxy ui...");
        int exitCode = SpringApplication.exit(applicationContext);
        if (exitCode != 0) {
            log.warn("Exit of tiger proxy ui not successful - exit code: " + exitCode);
        }
        if (StringUtils.isEmpty(noSystemExit)) {
            System.exit(0);
        }
    }

    @PostMapping(value = "/uploadReport")
    public void uploadReport(@RequestBody String htmlReport) {
        if (applicationConfiguration.getUploadUrl().equals("UNDEFINED")) {
            throw new TigerProxyConfigurationException("Upload feature is not configured!");
        }
        log.info("Uploading report...");
        performUploadReport(URLDecoder.decode(htmlReport, StandardCharsets.UTF_8));
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
        String filename = applicationConfiguration.getFilenamePattern()
            .replace("${DATE}", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .replace("${TIME}", LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS")));
        String uploadUrl = applicationConfiguration.getUploadUrl() + filename;

        try {
            URL serverUrl = new URL(uploadUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) serverUrl.openConnection();
            String boundaryString = "----TigerProxyReport";
            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod("POST");
            urlConnection.addRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundaryString);
            if (applicationConfiguration.getUsername() != null) {
                String auth = applicationConfiguration.getUsername() + ":"
                    + applicationConfiguration.getPassword();
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
                    // Todo: was genau soll hier rausgeschrieben werden?
                    zos.write(tigerProxy.getTigerProxyConfiguration().toString().getBytes(StandardCharsets.UTF_8));
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

    @PostMapping(value = "/traffic")
    public void importTrafficFromFile(@RequestBody String rawTraffic) {
        RbelFileWriterUtils.convertFromRbelFile(
            rawTraffic, tigerProxy.getRbelLogger().getRbelConverter());
    }

    private List<RbelElement> messsages() {
        return Collections.unmodifiableList(tigerProxy.getRbelMessages());
    }
}
