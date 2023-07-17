package de.gematik.test.tiger.glue;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelSerializationResult;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.exception.TigerHttpGlueCodeException;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.Method;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;

@Slf4j
public class HttpGlueCode {

    private static final TigerTypedConfigurationKey<Boolean> executeBlocking = new TigerTypedConfigurationKey<>(
        new TigerConfigurationKey("tiger", "httpClient", "executeBlocking"), Boolean.class, false);
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static RbelLogger rbelLogger;
    private static RbelWriter rbelWriter;

    private static RequestSpecification givenDefaultSpec() {
        final RequestSpecification requestSpecification = RestAssured.given();
        return requestSpecification
            .headers(TigerGlobalConfiguration.readMap("tiger", "httpClient", "defaultHeader"));
    }

    private static String resolveToString(String value) {
        return resolve(value).getContentAsString();
    }

    private static RbelSerializationResult resolve(String value) {
        final String resolvedInput = TigerGlobalConfiguration.resolvePlaceholders(value);
        if (TigerDirector.getLibConfig().getHttpClientConfig().isActivateRbelWriter()) {
            final RbelElement input = getRbelConverter().convertElement(resolvedInput, null);
            return getRbelWriter().serialize(input, new TigerJexlContext().withRootElement(input));
        } else {
            return RbelSerializationResult.withUnknownType(resolvedInput.getBytes(DEFAULT_CHARSET));
        }
    }

    private static void executeCommandWithContingentWait(ThrowingRunnable command) {
        if (executeBlocking.getValueOrDefault()) {
            executeCommandInBackground(command);
        } else {
            try {
                command.run();
            } catch (Exception e) {
                throw new TigerHttpGlueCodeException("Error during request execution", e);
            }
        }
    }

    private static void executeCommandInBackground(ThrowingRunnable command) {
        TigerDirector.getTigerTestEnvMgr().getCachedExecutor().submit(() -> {
            try {
                command.run();
            } catch (Exception e) {
                throw new TigerHttpGlueCodeException("Error during request execution", e);
            }
        });
    }

    private static RbelWriter getRbelWriter() {
        assureRbelIsInitialized();
        return rbelWriter;
    }

    private static RbelConverter getRbelConverter() {
        assureRbelIsInitialized();
        return rbelLogger.getRbelConverter();
    }

    private static void assureRbelIsInitialized() {
        if (rbelWriter == null) {
            rbelLogger = RbelLogger.build(RbelConfiguration.builder()
                .activateAsn1Parsing(true)
                .initializers(Optional.ofNullable(TigerDirector.getTigerTestEnvMgr().getConfiguration().getTigerProxy().getKeyFolders())
                    .stream()
                    .flatMap(List::stream)
                    .map(RbelKeyFolderInitializer::new)
                    .map(init -> (Consumer<RbelConverter>) init)
                    .toList())
                .build());
            rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
        }
    }

    /**
     * @param name name of the HTTP request method
     * @return an actual {@link Method}
     */
    @ParameterType("GET|POST|DELETE|PUT|OPTIONS")
    public Method requestType(String name) {
        return Method.valueOf(name);
    }

    /**
     * Sends an empty request via the selected method. Placeholders in address will be resolved.
     *
     * @param method  HTTP request method (see {@link Method})
     * @param address target address
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @SneakyThrows
    @When("TGR send empty {requestType} request to {string}")
    @When("TGR eine leere {requestType} Anfrage an {string} sendet")
    @Then("TGR sende eine leere {requestType} Anfrage an {string}")
    public void sendEmptyRequest(Method method, String address) {
        log.info("Sending empty {} request to {}", method, address);
        executeCommandWithContingentWait(() ->
            givenDefaultSpec().request(method, new URI(resolveToString(address))));
    }


    /**
     * Sends an empty request via the selected method. Placeholders in address will be resolved.
     * <p>
     * This method is NON-BLOCKING, meaning it will not wait for the response before continuing the test.
     *
     * @param method  HTTP request method (see {@link Method})
     * @param address target address
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @SneakyThrows
    @When("TGR send empty {requestType} request to {string} without waiting for the response")
    @Then("TGR sende eine leere {requestType} Anfrage an {string} ohne auf Antwort zu warten")
    public void sendEmptyRequestNonBlocking(Method method, String address) {
        log.info("Sending empty {} request to {}", method, address);
        executeCommandInBackground(() ->
            givenDefaultSpec().request(method, new URI(resolveToString(address))));
    }

    /**
     * Sends an empty request via the selected method and expands the list of default headers with the headers provided by the caller. Placeholders in address
     * and in the data table will be resolved. Example:
     * <pre>
     *     When TGR send empty GET request to "${myAddress}" with headers:
     *      | name | bob |
     *      | age  | 27  |
     * </pre>
     * This will add two headers (name and age) with the respective values "bob" and "27" on top of the headers which are used by default.
     *
     * @param method        HTTP request method (see {@link Method})
     * @param address       target address
     * @param customHeaders Markdown table of custom headers and their values
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @SneakyThrows
    @When("TGR send empty {requestType} request to {string} with headers:")
    @When("TGR eine leere {requestType} Anfrage an {string} und den folgenden Headern sendet:")
    @Then("TGR sende eine leere {requestType} Anfrage an {string} mit folgenden Headern:")
    public void sendEmptyRequestWithHeaders(Method method, String address, DataTable customHeaders) {
        log.info("Sending empty {} request with headers to {}", method, address);
        Map<String, String> defaultHeaders = TigerGlobalConfiguration.readMap("tiger", "httpClient", "defaultHeader");
        defaultHeaders.putAll(resolveMap(customHeaders.asMap()));
        executeCommandWithContingentWait(() ->
            givenDefaultSpec()
                .headers(defaultHeaders)
                .request(method, new URI(resolveToString(address))));
    }

    /**
     * Sends an empty request via the selected method and expands the list of default headers with the headers provided by the caller. Placeholders in address
     * and in the data table will be resolved.
     * <p>
     * This method is NON-BLOCKING, meaning it will not wait for the response before continuing the test.
     *
     * @param method        HTTP request method (see {@link Method})
     * @param address       target address
     * @param customHeaders Markdown table of custom headers and their values
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @SneakyThrows
    @When("TGR send empty {requestType} request to {string} without waiting for the response with headers:")
    @Then("TGR sende eine leere {requestType} Anfrage an {string} ohne auf Antwort zu warten mit folgenden Headern:")
    public void sendEmptyRequestWithHeadersNonBlocking(Method method, String address, DataTable customHeaders) {
        log.info("Sending empty {} request with headers to {}", method, address);
        Map<String, String> defaultHeaders = TigerGlobalConfiguration.readMap("tiger", "httpClient", "defaultHeader");
        defaultHeaders.putAll(resolveMap(customHeaders.asMap()));
        executeCommandInBackground(() ->
            givenDefaultSpec()
                .headers(defaultHeaders)
                .request(method, new URI(resolveToString(address))));
    }

    /**
     * Sends a request containing the provided body via the selected method. Placeholders in the body and in address will be resolved.
     *
     * @param method  HTTP request method (see {@link Method})
     * @param body    to be included in the request
     * @param address target address
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @SneakyThrows
    @When("TGR send {requestType} request to {string} with body {string}")
    @When("TGR eine leere {requestType} Anfrage an {string} und dem folgenden body {string} sendet")
    @Then("TGR sende eine {requestType} Anfrage an {string} mit Body {string}")
    public void sendRequestWithBody(Method method, String address, String body) {
        log.info("Sending {} request with body to {}", method, address);
        executeCommandWithContingentWait(() -> sendResolvedBody(method, address, body));
    }

    /**
     * Sends a request containing the provided body via the selected method. Placeholders in the body and in address will be resolved.
     * <p>
     * This method is NON-BLOCKING, meaning it will not wait for the response before continuing the test. ======= givenDefaultSpec() .body(resolve(body))
     * .request(method, new URI(resolveToString(address))); }
     * <p>
     * /** Expands the list of default headers with the provided key-value pair. If the key already exists, then the existing value is overwritten by the new
     * value. Placeholders in the header name and in its value will be resolved. >>>>>>> master
     *
     * @param method  HTTP request method (see {@link Method})
     * @param body    to be included in the request
     * @param address target address
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */

    @SneakyThrows
    @When("TGR send {requestType} request to {string} with body {string} without waiting for the response")
    @Then("TGR sende eine {requestType} Anfrage an {string} mit Body {string} ohne auf Antwort zu warten")
    public void sendRequestWithBodyNonBlocking(Method method, String address, String body) {
        log.info("Sending {} request with body to {}", method, address);
        executeCommandInBackground(() -> sendResolvedBody(method, address, body));
    }

    private static void sendResolvedBody(Method method, String address, String body) throws URISyntaxException {
        final RbelSerializationResult resolved = resolve(body);
        final RequestSpecification requestSpecification = givenDefaultSpec();
        resolved.getContentType()
            .map(RbelContentType::getContentTypeString)
            .filter(o -> StringUtils.isEmpty(((RequestSpecificationImpl) requestSpecification).getContentType()))
            .ifPresent(requestSpecification::contentType);
        requestSpecification
            .body(resolved.getContent())
            .request(method, new URI(resolveToString(address)));
    }

    /**
     * Sends a request via the selected method. The request is expanded by the provided key-value pairs. Placeholders in keys and values will be resolved.
     * Example:
     * <pre>
     *      When Send POST request to "http://my.address.com" with
     *       | ${configured_param_name}   | state                     | redirect_uri        |
     *       | client_id                  | ${configured_state_value} | https://my.redirect |
     * </pre>
     * <br>
     * <b>NOTE:</b> This Markdown table must contain exactly 1 row of headers and 1 row of
     * values.
     * </p>
     *
     * @param method     HTTP request method (see {@link Method})
     * @param address    target address
     * @param parameters to be sent with the request
     * @see RequestSpecification#formParams(Map)
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    @SneakyThrows
    @When("TGR send {requestType} request to {string} with:")
    @When("TGR eine {requestType} Anfrage an {string} mit den folgenden Daten sendet:")
    @Then("TGR sende eine {requestType} Anfrage an {string} mit folgenden Daten:")
    public void sendRequestWithParams(Method method, String address, DataTable parameters) {
        List<Map<String, String>> dataAsMaps = parameters.asMaps();
        if (dataAsMaps.size() != 1) {
            throw new AssertionError("Expected exactly one entry for data table, "
                + "got " + dataAsMaps.size());
        }
        executeCommandWithContingentWait(() ->
            givenDefaultSpec()
                .formParams(resolveMap(dataAsMaps.get(0)))
                .request(method, new URI(resolveToString(address))));
    }

    /**
     * Sends a request via the selected method. The request is expanded by the provided key-value pairs. Placeholders in keys and values will be resolved.
     * <p>
     * This method is NON-BLOCKING, meaning it will not wait for the response before continuing the test.
     *
     * @param method     HTTP request method (see {@link Method})
     * @param address    target address
     * @param parameters to be sent with the request
     * @see RequestSpecification#formParams(Map)
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @SneakyThrows
    @When("TGR send {requestType} request to {string} without waiting for the response with:")
    @Then("TGR sende eine {requestType} Anfrage an {string} ohne auf Antwort zu warten mit folgenden Daten:")
    public void sendRequestWithParamsNonBlocking(Method method, String address, DataTable parameters) {
        List<Map<String, String>> dataAsMaps = parameters.asMaps();
        if (dataAsMaps.size() != 1) {
            throw new AssertionError("Expected exactly one entry for data table, "
                + "got " + dataAsMaps.size());
        }
        executeCommandInBackground(() ->
            givenDefaultSpec()
                .formParams(resolveMap(dataAsMaps.get(0)))
                .request(method, new URI(resolveToString(address))));
    }

    /**
     * Expands the list of default headers with the provided key-value pair. If the key already exists, then the existing value is overwritten by the new value.
     * Placeholders in the header name and in its value will be resolved.
     *
     * @param header key
     * @param value  to be stored under the given key
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @When("TGR set default header {string} to {string}")
    @When("TGR den default header {string} auf den Wert {string} setzen")
    @Then("TGR setze den default header {string} auf den Wert {string}")
    public void setDefaultHeader(String header, String value) {
        TigerGlobalConfiguration.putValue("tiger.httpClient.defaultHeader." + resolveToString(header), resolveToString(value));
    }

    private Map<String, String> resolveMap(Map<String, String> map) {
        return map.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> resolveToString(entry.getKey()),
                entry -> resolveToString(entry.getValue())));
    }
}
