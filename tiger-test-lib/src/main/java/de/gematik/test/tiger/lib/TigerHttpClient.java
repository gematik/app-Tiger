package de.gematik.test.tiger.lib;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelSerializationResult;
import de.gematik.test.tiger.RbelUtil;
import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
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

public class TigerHttpClient {

  public static final String KEY_HTTP_CLIENT = "httpClient";
  public static final String KEY_TIGER = "tiger";
  private static final RbelUtil RBEL_UTIL = new RbelUtil();
  private static final TigerTypedConfigurationKey<Boolean> executeBlocking =
      new TigerTypedConfigurationKey<>(
          new TigerConfigurationKey(KEY_TIGER, KEY_HTTP_CLIENT, "executeBlocking"),
          Boolean.class,
          true);
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
  public static final String KEY_DEFAULT_HEADER = "defaultHeader";

    private TigerHttpClient() {
        // hide constructor
    }

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

  public static void applyRedirectConfig(RedirectConfig newRedirectConfig) {
    RestAssured.config = RestAssured.config.redirect(newRedirectConfig);
  }

  public static void resetRedirectConfig() {
    applyRedirectConfig(new RedirectConfig());
  }

  public static String resolveToString(String value) {
    return resolve(value).getContentAsString();
  }

  public static RbelSerializationResult resolve(String value) {
    final String resolvedInput = TigerGlobalConfiguration.resolvePlaceholders(value);
    if (TigerDirector.getLibConfig().getHttpClientConfig().isActivateRbelWriter()) {
      final RbelElement input = RBEL_UTIL.getRbelConverter().convertElement(resolvedInput, null);
      return RBEL_UTIL
          .getRbelWriter()
          .serialize(input, new TigerJexlContext().withRootElement(input));
    } else {
      return RbelSerializationResult.withUnknownType(resolvedInput.getBytes(DEFAULT_CHARSET));
    }
  }

  public static void sendResolvedBody(Method method, URI address, String body) {
    sendResolvedBody(method, address, null, body);
  }

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
