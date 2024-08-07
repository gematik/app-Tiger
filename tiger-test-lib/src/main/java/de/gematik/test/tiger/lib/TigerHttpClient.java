package de.gematik.test.tiger.lib;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelSerializationResult;
import de.gematik.test.tiger.RbelLoggerWriter;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.util.ResetableLazyInitializer;
import de.gematik.test.tiger.lib.exception.TigerHttpGlueCodeException;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.http.Method;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertionsProvider;

/**
 * This class provides methods to send requests with RestAssured. It also provides methods to
 * resolve placeholders in the request body and send the resolved body.
 */
public class TigerHttpClient {

  public static final String KEY_HTTP_CLIENT = "httpClient";
  public static final String KEY_TIGER = "tiger";
  public static final String KEY_DEFAULT_HEADER = "defaultHeader";

  private static final ResetableLazyInitializer<RbelLoggerWriter> RBEL_LOGGER_WRITER =
      new ResetableLazyInitializer<>(RbelLoggerWriter::new);

  private static final TigerTypedConfigurationKey<Boolean> executeBlocking =
      new TigerTypedConfigurationKey<>(
          new TigerConfigurationKey(KEY_TIGER, KEY_HTTP_CLIENT, "executeBlocking"),
          Boolean.class,
          true);
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  private TigerHttpClient() {
    // do not instantiate
  }

  public static void reset() {
    // creating a new instance of the RbelLoggerWriter loads configuration from the
    // TigerGlobalConfiguration
    // If we use different configuration in different unit tests we need to reset this instance.
    RBEL_LOGGER_WRITER.reset();
  }

  /**
   * Create a configurable RequestSpecification with default Tiger headers. Example:
   *
   * <pre>
   *  givenDefaultSpec()
   *    .formParams(resolveMap(dataAsMaps.get(0), true))
   *    .headers(Map.of("header", "value"))
   *    .contentType(ContentType.JSON)
   *    .request(method, address));
   * </pre>
   *
   * @return configurable RequestSpecification
   */
  public static RequestSpecification givenDefaultSpec() {
    var encoderConfig =
        RestAssured.config()
            .getEncoderConfig()
            .appendDefaultContentCharsetToContentTypeIfUndefined(true);

    var requestSpecification =
        RestAssured.given()
            .urlEncodingEnabled(false)
            .config(RestAssured.config().encoderConfig(encoderConfig));

    requestSpecification.headers(
        TigerGlobalConfiguration.readMap(KEY_TIGER, KEY_HTTP_CLIENT, KEY_DEFAULT_HEADER));

    Optional.ofNullable(((QueryableRequestSpecification) requestSpecification).getContentType())
        .ifPresent(ct -> setExactContentTypeHeader(requestSpecification, ct));
    return requestSpecification;
  }

  /**
   * Execute a command in the background. This is useful for long-running tasks that should not
   * block the main thread.
   *
   * @param command the command to execute
   */
  public static void executeCommandInBackground(SoftAssertionsProvider.ThrowingRunnable command) {
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

  /**
   * Execute a command with a contingent wait. If the configuration value `executeBlocking` is set
   * to `true`, the command is executed immediately. Otherwise, the command is executed in the
   * background.
   *
   * @param command the command to execute
   */
  public static void executeCommandWithContingentWait(
      SoftAssertionsProvider.ThrowingRunnable command) {
    if (Boolean.TRUE.equals(executeBlocking.getValueOrDefault())) {
      try {
        command.run();
      } catch (Exception e) {
        throw new TigerHttpGlueCodeException("Error during request execution", e);
      }
    } else {
      executeCommandInBackground(command);
    }
  }

  /**
   * Apply a new RedirectConfig to the RestAssured configuration.
   *
   * @param newRedirectConfig the new RedirectConfig to apply
   */
  public static void applyRedirectConfig(RedirectConfig newRedirectConfig) {
    RestAssured.config = RestAssured.config.redirect(newRedirectConfig);
  }

  /** Reset the RedirectConfig to the default configuration. */
  public static void resetRedirectConfig() {
    applyRedirectConfig(new RedirectConfig());
  }

  /**
   * Resolve placeholders in the given value and return the result as a string.
   *
   * @param value the value to resolve
   * @return the value, but resolved
   */
  public static String resolveToString(String value) {
    return resolve(value).getContentAsString();
  }

  /**
   * Resolve placeholders in the given value and return the result as a byte array.
   *
   * @param value the value to resolve
   * @return the value, but resolved as a byte array
   */
  public static RbelSerializationResult resolve(String value) {
    final String resolvedInput = TigerGlobalConfiguration.resolvePlaceholders(value);
    if (TigerDirector.getLibConfig().getHttpClientConfig().isActivateRbelWriter()) {
      final RbelElement input =
          RBEL_LOGGER_WRITER.get().getRbelConverter().convertElement(resolvedInput, null);
      return RBEL_LOGGER_WRITER
          .get()
          .getRbelWriter()
          .serialize(input, new TigerJexlContext().withRootElement(input));
    } else {
      return RbelSerializationResult.withUnknownType(resolvedInput.getBytes(DEFAULT_CHARSET));
    }
  }

  /**
   * Send a request with the given method, address and body. The body is resolved before sending.
   *
   * @param method the HTTP method to use
   * @param address the URI to send the request to
   * @param body the body (which is to be resolved) of the request
   */
  public static void sendResolvedBody(Method method, URI address, String body) {
    sendResolvedBody(method, address, null, body);
  }

  /**
   * Send a request with the given method, address and body. The body is resolved before sending.
   *
   * @param method the HTTP method to use
   * @param address the URI to send the request to
   * @param contentType the content type of the request
   * @param body the body (which is to be resolved) of the request
   */
  public static void sendResolvedBody(Method method, URI address, String contentType, String body) {
    final RbelSerializationResult resolved = resolve(body);
    final RequestSpecification requestSpecification = givenDefaultSpec();

    if (contentType != null) {
      setExactContentTypeHeader(requestSpecification, contentType);
    }
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

  private static void setExactContentTypeHeader(
      RequestSpecification requestSpecification, String contentType) {
    requestSpecification.config(
        RestAssured.config()
            .encoderConfig(
                EncoderConfig.encoderConfig()
                    .appendDefaultContentCharsetToContentTypeIfUndefined(false)));
    requestSpecification.contentType(contentType);
  }
}
