/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

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
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.config.RedirectConfig;
import io.restassured.http.Method;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;

@Slf4j
public class HttpGlueCode {

  public static final String KEY_HTTP_CLIENT = "httpClient";
  public static final String KEY_TIGER = "tiger";
  private static final TigerTypedConfigurationKey<Boolean> executeBlocking =
      new TigerTypedConfigurationKey<>(
          new TigerConfigurationKey(KEY_TIGER, KEY_HTTP_CLIENT, "executeBlocking"),
          Boolean.class,
          false);
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
  public static final String KEY_DEFAULT_HEADER = "defaultHeader";
  private static RbelLogger rbelLogger;
  private static RbelWriter rbelWriter;

  private static RequestSpecification givenDefaultSpec() {
    final RequestSpecification requestSpecification = RestAssured.given().urlEncodingEnabled(false);
    return requestSpecification.headers(
        TigerGlobalConfiguration.readMap(KEY_TIGER, KEY_HTTP_CLIENT, KEY_DEFAULT_HEADER));
  }

  private static void applyRedirectConfig(RedirectConfig newRedirectConfig) {
    RestAssured.config = RestAssured.config.redirect(newRedirectConfig);
  }

  private static void resetRedirectConfig() {
    applyRedirectConfig(new RedirectConfig());
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
    TigerDirector.getTigerTestEnvMgr()
        .getCachedExecutor()
        .submit(
            () -> {
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
      rbelLogger =
          RbelLogger.build(
              RbelConfiguration.builder()
                  .activateAsn1Parsing(true)
                  .initializers(
                      Optional.ofNullable(
                              TigerDirector.getTigerTestEnvMgr()
                                  .getConfiguration()
                                  .getTigerProxy()
                                  .getKeyFolders())
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
    log.info("Sending empty {} request to {}", method, address);
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
      "TGR eine leere {requestType} Anfrage an {tigerResolvedUrl} und den folgenden Headern sendet:")
  @Dann("TGR sende eine leere {requestType} Anfrage an {tigerResolvedUrl} mit folgenden Headern:")
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
      "TGR send empty {requestType} request to {tigerResolvedUrl} without waiting for the response with headers:")
  @Dann(
      "TGR sende eine leere {requestType} Anfrage an {tigerResolvedUrl} ohne auf Antwort zu warten mit folgenden Headern:")
  public void sendEmptyRequestWithHeadersNonBlocking(
      Method method, URI address, DataTable customHeaders) {
    log.info("Sending empty {} request with headers to {}", method, address);
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
      "TGR eine leere {requestType} Anfrage an {tigerResolvedUrl} und dem folgenden body {string} sendet")
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
   * test. ======= givenDefaultSpec() .body(resolve(body)) .request(method, new
   * URI(resolveToString(address))); }
   *
   * <p>/** Expands the list of default headers with the provided key-value pair. If the key already
   * exists, then the existing value is overwritten by the new value. Placeholders in the header
   * name and in its value will be resolved. >>>>>>> master
   *
   * @param method HTTP request method (see {@link Method})
   * @param body to be included in the request
   * @param address target address
   * @see TigerGlobalConfiguration#resolvePlaceholders(String)
   */
  @SneakyThrows
  @When(
      "TGR send {requestType} request to {tigerResolvedUrl} with body {string} without waiting for the"
          + " response")
  @Dann(
      "TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} mit Body {string} ohne auf Antwort zu"
          + " warten")
  public void sendRequestWithBodyNonBlocking(Method method, URI address, String body) {
    log.info("Sending {} request with body to {}", method, address);
    executeCommandInBackground(() -> sendResolvedBody(method, address, body));
  }

  private static void sendResolvedBody(Method method, URI address, String body) {
    final RbelSerializationResult resolved = resolve(body);
    final RequestSpecification requestSpecification = givenDefaultSpec();
    resolved
        .getContentType()
        .map(RbelContentType::getContentTypeString)
        .filter(
            o ->
                StringUtils.isEmpty(
                    ((RequestSpecificationImpl) requestSpecification).getContentType()))
        .ifPresent(requestSpecification::contentType);
    requestSpecification.body(resolved.getContent()).request(method, address);
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
      "TGR eine {requestType} Anfrage an {tigerResolvedUrl} mit den folgenden mehrzeiligen Daten sendet:")
  @Dann(
      "TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} mit folgenden mehrzeiligen Daten:")
  public void sendRequestWithMultiLineBody(Method method, URI address, String body) {
    log.info("Sending complex {} request with body to {}", method, address);
    executeCommandWithContingentWait(() -> sendResolvedBody(method, address, body));
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
      "TGR sende eine {requestType} Anfrage an {tigerResolvedUrl} ohne auf Antwort zu warten mit folgenden"
          + " Daten:")
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
                    .sorted(Comparator.comparing(source -> source.getSourceType().getPrecedence()))
                    .forEach(
                        source ->
                            source.removeValue(
                                new TigerConfigurationKey(
                                    KEY_TIGER, KEY_HTTP_CLIENT, KEY_DEFAULT_HEADER, key))));
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
   * Resets the global configuration of the HttpClient to its default behaviour of automatically
   * following redirects.
   */
  @When("TGR reset HttpClient followRedirects configuration")
  @Wenn("TGR HttpClient followRedirects Konfiguration zurücksetzt")
  public void resetHttpClientRedirectConfiguration() {
    resetRedirectConfig();
  }
}
