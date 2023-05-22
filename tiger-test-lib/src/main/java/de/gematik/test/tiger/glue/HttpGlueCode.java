package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpGlueCode {

    private static RequestSpecification givenDefaultSpec() {
        final RequestSpecification requestSpecification = RestAssured.given();
        return requestSpecification
                .headers(TigerGlobalConfiguration.readMap("tiger", "httpClient", "defaultHeader"));
    }

    private String resolve(String value) {
        return TigerGlobalConfiguration.resolvePlaceholders(value);
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
        givenDefaultSpec().request(method, new URI(resolve(address)));
    }

    /**
     * Sends an empty request via the selected method and expands the list of default headers with
     * the headers provided by the caller. Placeholders in address and in the data table will be resolved.
     * Example:
     * <pre>
     *     When TGR send empty GET request to "${myAddress}" with headers:
     *      | name | bob |
     *      | age  | 27  |
     * </pre>
     * This will add two headers (name and age) with the respective values "bob" and "27" on top of
     * the headers which are used by default.
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
        givenDefaultSpec()
                .headers(defaultHeaders)
                .request(method, new URI(resolve(address)));
    }

    /**
     * Sends a request containing the provided body via the selected method. Placeholders in the
     * body and in address will be resolved.
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
        givenDefaultSpec()
                .body(resolve(body))
                .request(method, new URI(resolve(address)));
    }

    /**
     * Expands the list of default headers with the provided key-value pair. If the key already
     * exists, then the existing value is overwritten by the new value. Placeholders in the
     * header name and in its value will be resolved.
     *
     * @param header key
     * @param value  to be stored under the given key
     * @see TigerGlobalConfiguration#resolvePlaceholders(String)
     */
    @When("TGR set default header {string} to {string}")
    @When("TGR den default header {string} auf den Wert {string} setzen")
    @Then("TGR setze den default header {string} auf den Wert {string}")
    public void setDefaultHeader(String header, String value) {
        TigerGlobalConfiguration.putValue("tiger.httpClient.defaultHeader." + resolve(header), resolve(value));
    }


    /**
     * Sends a request via the selected method. The request is expanded by the provided key-value
     * pairs. Placeholders in keys and values will be resolved.
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
        givenDefaultSpec()
                .formParams(resolveMap(dataAsMaps.get(0)))
                .request(method, new URI(resolve(address)));
    }

    private Map<String,String> resolveMap(Map<String,String> map) {
        return map.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> resolve(entry.getKey()),
                entry -> resolve(entry.getValue())));
    }
}
