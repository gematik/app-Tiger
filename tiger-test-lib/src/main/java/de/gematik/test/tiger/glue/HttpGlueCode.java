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
package de.gematik.test.tiger.glue;

import static de.gematik.test.tiger.lib.TigerHttpClient.*;

import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.annotation.FirstColumnKeyTable;
import de.gematik.test.tiger.glue.annotation.FirstRowKeyTable;
import de.gematik.test.tiger.glue.annotation.ResolvableArgument;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.config.RedirectConfig;
import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("unused") // glue code is used via reflection
@Slf4j
public class HttpGlueCode {

  /**
   * Sends an empty request via the selected method. Placeholders in address will be resolved.
   *
   * @param method HTTP request method (see {@link Method})
   * @param address target address
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When("TGR send empty {requestType} request to {tigerResolvedUrl}")
  @Wenn("TGR eine leere {requestType} Anfrage an {tigerResolvedUrl} sendet")
  @Dann("TGR sende eine leere {requestType} Anfrage an {tigerResolvedUrl}")
  public void sendEmptyRequest(Method method, URI address) {
    log.info("Sending empty {} request to {}", method, address);
    executeCommandWithContingentWait(() -> givenDefaultSpec().request(method, address));
  }

  /**
   * Sends an empty request via the selected method. Placeholders in address will be resolved.
   *
   * <p>This method is NON-BLOCKING, meaning it will not wait for the response before continuing the
   * test.
   *
   * @param method HTTP request method (see {@link Method})
   * @param address target address
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When(
      "TGR send empty {requestType} request to {tigerResolvedUrl} without waiting for the response")
  @Dann(
      "TGR sende eine leere {requestType} Anfrage an {tigerResolvedUrl} ohne auf Antwort zu warten")
  public void sendEmptyRequestNonBlocking(Method method, URI address) {
    log.info("Sending empty non-blocking {} request to {}", method, address);
    executeCommandInBackground(() -> givenDefaultSpec().request(method, address));
  }

  /**
   * Sends an empty request via the selected method and expands the list of default headers with the
   * headers provided by the caller. Placeholders in address and in the data table will be resolved.
   * Example:
   *
   * <pre>
   *     When TGR send empty GET request to "${myAddress}" with headers:
   *      | name | bob |
   *      | age  | 27  |
   * </pre>
   *
   * This will add two headers (name and age) with the respective values "bob" and "27" on top of
   * the headers which are used by default.
   *
   * @param method HTTP request method (see {@link Method})
   * @param address target address
   * @param customHeaders Markdown table of custom headers and their values
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When("TGR send empty {requestType} request to {tigerResolvedUrl} with headers:")
  @Wenn(
      "TGR eine leere {requestType} Anfrage an {tigerResolvedUrl} und den folgenden Headern"
          + " sendet:")
  @Dann("TGR sende eine leere {requestType} Anfrage an {tigerResolvedUrl} mit folgenden Headern:")
  @ResolvableArgument
  @FirstColumnKeyTable
  public void sendEmptyRequestWithHeaders(Method method, URI address, DataTable customHeaders) {
    log.info("Sending empty {} request with headers to {}", method, address);
    Map<String, String> defaultHeaders =
        TigerGlobalConfiguration.readMap(KEY_TIGER, KEY_HTTP_CLIENT, KEY_DEFAULT_HEADER);
    defaultHeaders.putAll(resolveMap(customHeaders.asMap(), false));
    executeCommandWithContingentWait(
        () -> givenDefaultSpec().headers(defaultHeaders).request(method, address));
  }

  /**
   * Sends an empty request via the selected method and expands the list of default headers with the
   * headers provided by the caller. Placeholders in address and in the data table will be resolved.
   *
   * <p>This method is NON-BLOCKING, meaning it will not wait for the response before continuing the
   * test.
   *
   * @param method HTTP request method (see {@link Method})
   * @param address target address
   * @param customHeaders Markdown table of custom headers and their values
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When(
      "TGR send empty {requestType} request to {tigerResolvedUrl} without waiting for the response"
          + " with headers:")
  @Dann(
      "TGR sende eine leere {requestType} Anfrage an {tigerResolvedUrl} ohne auf Antwort zu warten"
          + " mit folgenden Headern:")
  @ResolvableArgument
  @FirstColumnKeyTable
  public void sendEmptyRequestWithHeadersNonBlocking(
      Method method, URI address, DataTable customHeaders) {
    log.info("Sending empty {} non-blocking request with headers to {}", method, address);
    Map<String, String> defaultHeaders =
        TigerGlobalConfiguration.readMap(KEY_TIGER, KEY_HTTP_CLIENT, KEY_DEFAULT_HEADER);
    defaultHeaders.putAll(resolveMap(customHeaders.asMap(), false));
    executeCommandInBackground(
        () -> givenDefaultSpec().headers(defaultHeaders).request(method, address));
  }

  /**
   * Sends a request containing the provided body via the selected method. Placeholders in the body
   * and in address will be resolved.
   *
   * @param method HTTP request method (see {@link Method})
   * @param body to be included in the request
   * @param address target address
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When("TGR send {requestType} request to {tigerResolvedUrl} with body {string}")
  @Wenn(
      "TGR eine leere {requestType} Anfrage an {tigerResolvedUrl} und dem folgenden body {string}"
          + " sendet")
  @Dann("TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} mit Body {string}")
  public void sendRequestWithBody(Method method, URI address, String body) {
    log.info("Sending {} request with body to {}", method, address);
    executeCommandWithContingentWait(() -> sendResolvedBody(method, address, body));
  }

  /**
   * Sends a request containing the provided body via the selected method. Placeholders in the body
   * and in address will be resolved.
   *
   * <p>This method is NON-BLOCKING, meaning it will not wait for the response before continuing the
   * test.
   *
   * @param method HTTP request method (see {@link Method})
   * @param body to be included in the request
   * @param address target address
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When(
      "TGR send {requestType} request to {tigerResolvedUrl} with body {string} without waiting for"
          + " the response")
  @Dann(
      "TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} mit Body {string} ohne auf"
          + " Antwort zu warten")
  public void sendRequestWithBodyNonBlocking(Method method, URI address, String body) {
    log.info("Sending {} non-blocking request with body to {}", method, address);
    executeCommandInBackground(() -> sendResolvedBody(method, address, body));
  }

  /**
   * Sends a request via the selected method. The request is expanded by the provided key-value
   * pairs. Placeholders in keys and values will be resolved. The values must not be URL encoded, as
   * this is done by the step. Example:
   *
   * <pre>
   *      When Send POST request to "http://my.address.com" with
   *       | ${configured_param_name}   | state                     | redirect_uri        |
   *       | client_id                  | ${configured_state_value} | https://my.redirect |
   * </pre>
   *
   * <br>
   * <b>NOTE:</b> This Markdown table must contain exactly 1 row of headers and 1 row of values.
   *
   * @param method HTTP request method (see {@link Method})
   * @param address target address
   * @param parameters to be sent with the request
   * @see RequestSpecification#formParams(Map)
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  @SneakyThrows
  @When("TGR send {requestType} request to {tigerResolvedUrl} with:")
  @Wenn("TGR eine {requestType} Anfrage an {tigerResolvedUrl} mit den folgenden Daten sendet:")
  @Dann("TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} mit folgenden Daten:")
  @ResolvableArgument
  @FirstRowKeyTable
  public void sendRequestWithParams(Method method, URI address, DataTable parameters) {
    List<Map<String, String>> dataAsMaps = parameters.asMaps();
    if (dataAsMaps.size() != 1) {
      throw new AssertionError(
          "Expected exactly one entry for data table, " + "got " + dataAsMaps.size());
    }
    executeCommandWithContingentWait(
        () ->
            givenDefaultSpec()
                .formParams(resolveMap(dataAsMaps.get(0), true))
                .request(method, address));
  }

  /**
   * Sends a request via the selected method. For the given request's body placeholders in keys and
   * values will be resolved. This step is meant to be used for more complex bodys spanning multiple
   * lines.
   *
   * <p>Example:
   *
   * <pre>
   *      When TGR send POST request to "http://my.address.com" with multiline body:
   *       """
   *         {
   *              "name": "value",
   *              "object": { "member": "value" },
   *              "array" : [ 1,2,3,4]
   *         }
   *       """
   * </pre>
   *
   * <br>
   *
   * @param method HTTP request method (see {@link Method})
   * @param address target address
   * @param body body content of the request
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  @SneakyThrows
  @When("TGR send {requestType} request to {tigerResolvedUrl} with multiline body:")
  @Wenn(
      "TGR eine {requestType} Anfrage an {tigerResolvedUrl} mit den folgenden mehrzeiligen Daten"
          + " sendet:")
  @Dann(
      "TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} mit folgenden mehrzeiligen"
          + " Daten:")
  @ResolvableArgument
  public void sendRequestWithMultiLineBody(Method method, URI address, String body) {
    log.info("Sending complex {} request with body to {}", method, address);
    executeCommandWithContingentWait(() -> sendResolvedBody(method, address, body));
  }

  @SneakyThrows
  @When(
      "TGR send {requestType} request to {tigerResolvedUrl} with contentType {string} and multiline"
          + " body:")
  @Wenn(
      "TGR eine {requestType} Anfrage an {tigerResolvedUrl} mit ContentType {string} und den"
          + " folgenden mehrzeiligen Daten sendet:")
  @Dann(
      "TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} mit ContentType {string} und"
          + " folgenden mehrzeiligen Daten:")
  @ResolvableArgument
  public void sendRequestWithMultiLineBody(
      Method method, URI address, String contentType, String body) {
    log.info("Sending complex {} request with body to {}", method, address);
    executeCommandWithContingentWait(() -> sendResolvedBody(method, address, contentType, body));
  }

  /**
   * Sends a request via the selected method. The request is expanded by the provided key-value
   * pairs. Placeholders in keys and values will be resolved. The values must not be URL encoded, as
   * this is done by the step.
   *
   * <p>This method is NON-BLOCKING, meaning it will not wait for the response before continuing the
   * test.
   *
   * @param method HTTP request method (see {@link Method})
   * @param address target address
   * @param parameters to be sent with the request
   * @see RequestSpecification#formParams(Map)
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When(
      "TGR send {requestType} request to {tigerResolvedUrl} without waiting for the response with:")
  @Dann(
      "TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} ohne auf Antwort zu warten mit"
          + " folgenden Daten:")
  @ResolvableArgument
  @FirstRowKeyTable
  public void sendRequestWithParamsNonBlocking(Method method, URI address, DataTable parameters) {
    List<Map<String, String>> dataAsMaps = parameters.asMaps();
    if (dataAsMaps.size() != 1) {
      throw new AssertionError(
          "Expected exactly one entry for data table, " + "got " + dataAsMaps.size());
    }
    executeCommandInBackground(
        () ->
            givenDefaultSpec()
                .formParams(resolveMap(dataAsMaps.get(0), true))
                .request(method, address));
  }

  /**
   * Expands the list of default headers with the provided key-value pair. If the key already
   * exists, then the existing value is overwritten by the new value. Placeholders in the header
   * name and in its value will be resolved.
   *
   * @param header key
   * @param value to be stored under the given key
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @When("TGR set default header {tigerResolvedString} to {tigerResolvedString}")
  @Wenn("TGR den default header {tigerResolvedString} auf den Wert {tigerResolvedString} setzen")
  @Dann("TGR setze den default header {tigerResolvedString} auf den Wert {tigerResolvedString}")
  public void setDefaultHeader(String header, String value) {
    TigerGlobalConfiguration.putValue(
        KEY_TIGER + "." + KEY_HTTP_CLIENT + "." + KEY_DEFAULT_HEADER + "." + header, value);
  }

  /**
   * Expands the list of default headers with the provided key-value pairs. If the key already
   * exists, then the existing value is overwritten by the new value. Placeholders in the header
   * names and in their values will be resolved.
   *
   * @param docstring multiline doc string, one key value pair per line
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @When("TGR set default headers:")
  @Dann("TGR setze folgende default headers:")
  @Wenn("TGR folgende default headers gesetzt werden:")
  @ResolvableArgument
  public void setDefaultHeaders(String docstring) {
    Arrays.stream(docstring.split("\n"))
        .filter(line -> !line.isEmpty())
        .filter(line -> line.contains("="))
        .map(
            line ->
                List.of(
                    StringUtils.substringBefore(line, "="), StringUtils.substringAfter(line, "=")))
        .forEach(
            kvp ->
                TigerGlobalConfiguration.putValue(
                    KEY_TIGER
                        + "."
                        + KEY_HTTP_CLIENT
                        + "."
                        + KEY_DEFAULT_HEADER
                        + "."
                        + resolveToString(kvp.get(0)).trim(),
                    resolveToString(kvp.get(1)).trim()));
  }

  /** Clear all default headers set in previous steps. */
  @When("TGR clear all default headers")
  @Wenn("TGR lösche alle default headers")
  public void clearDefaultHeaders() {
    TigerGlobalConfiguration.readMap(KEY_TIGER, KEY_HTTP_CLIENT, KEY_DEFAULT_HEADER)
        .forEach(
            (key, value) ->
                TigerGlobalConfiguration.listSources().stream()
                    .sorted(Comparator.comparing(source -> source.getPrecedence().getValue()))
                    .forEach(
                        source ->
                            source.removeValue(
                                new TigerConfigurationKey(
                                    KEY_TIGER, KEY_HTTP_CLIENT, KEY_DEFAULT_HEADER, key))));
  }

  /**
   * Modifies the global configuration of the HttpClient to not automatically follow redirects. All
   * following requests will use the modified configuration.
   */
  @When("TGR disable HttpClient followRedirects configuration")
  @Wenn("TGR HttpClient followRedirects Konfiguration deaktiviert")
  public void disableHttpClientFollowRedirects() {
    RedirectConfig newRedirectConfig =
        RestAssured.config.getRedirectConfig().followRedirects(false);

    applyRedirectConfig(newRedirectConfig);
  }

  /**
   * Resets the global configuration of the HttpClient to its default behavior of automatically
   * following redirects.
   */
  @When("TGR reset HttpClient followRedirects configuration")
  @Wenn("TGR HttpClient followRedirects Konfiguration zurücksetzt")
  public void resetHttpClientRedirectConfiguration() {
    resetRedirectConfig();
  }

  private Map<String, String> resolveMap(Map<String, String> map, boolean encoded) {
    return map.entrySet().stream()
        .collect(
            Collectors.toMap(
                entry -> resolveToString(entry.getKey()),
                entry ->
                    encoded
                        ? URLEncoder.encode(
                            resolveToString(entry.getValue()), StandardCharsets.UTF_8)
                        : resolveToString(entry.getValue())));
  }
}
