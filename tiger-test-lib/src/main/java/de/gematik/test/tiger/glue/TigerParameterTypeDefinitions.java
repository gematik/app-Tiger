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
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibConfig;
import de.gematik.test.tiger.lib.enums.ModeType;
import io.cucumber.java.ParameterType;
import io.restassured.http.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

/**
 * contains ParameterType definitions for cucumber prarameters. The method name is used as name of
 * the parameter type in cucumber steps. The regex is used to check if the parameter value matches
 * using Regex. All parameter types do support resolution using the TigerGlobalConfiguration.
 *
 * @see TigerGlobalConfiguration#resolvePlaceholders(String)
 * @see https://github.com/cucumber/cucumber-expressions
 */
public class TigerParameterTypeDefinitions {
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
  private static RbelLogger rbelLogger;
  private static RbelWriter rbelWriter;

  protected TigerParameterTypeDefinitions() {}

  /**
   * @param name name of the HTTP request method
   * @return an actual {@link Method}
   */
  @ParameterType("GET|POST|DELETE|PUT|OPTIONS|\".*\"|'.*'")
  public static Method requestType(String name) {
    return Method.valueOf(resolveToString(name));
  }

  /**
   * @param mode type of data format.
   * @return an actual {@link ModeType}
   */
  @ParameterType("XML|JSON|\".*\"|'.*'")
  public static ModeType modeType(String mode) {
    return ModeType.valueOf(resolveToString(mode));
  }

  /**
   * resolves given value by replacing all ${..}, !{...} tokens with their (RbelPath, JEXL)
   * evaluated string value
   *
   * @param string string value of the url
   * @return the resolved string
   */
  @ParameterType("\".*\"|'.*'")
  public static String tigerResolvedString(String string) {
    return resolveToString(string);
  }

  /**
   * resolves given value by replacing all ${..}, !{...} tokens with their (RbelPath, JEXL)
   * evaluated string value and converting
   *
   * @param address string value of the url
   * @return Enum value
   */
  @SneakyThrows
  @ParameterType("\".*\"|'.*'")
  public static @NotNull URI tigerResolvedUrl(String address) {
    return new URI(resolveToString(address));
  }

  /**
   * replaces String values with its enum value in {@link RbelContentType}
   *
   * @param value string value in enum
   * @return Enum value
   */
  @ParameterType("XML|JSON|JWE|JWT|BEARER_TOKEN|URL|\".*\"|'.*'")
  public static RbelContentType rbelContentType(String value) {
    return RbelContentType.seekValueFor(resolveToString(value));
  }

  private static String resolveToString(String value) {
    if (value.charAt(0) == '"' || value.charAt(0) == '\'') {
      return resolve(value.substring(1, value.length() - 1)).getContentAsString();
    }
    return resolve(value).getContentAsString();
  }

  private static RbelSerializationResult resolve(String value) {
    final String resolvedInput = TigerGlobalConfiguration.resolvePlaceholders(value);
    TigerLibConfig libConfig = TigerDirector.getLibConfig();
    if (libConfig != null && libConfig.getHttpClientConfig().isActivateRbelWriter()) {
      final RbelElement input = getRbelConverter().convertElement(resolvedInput, null);
      return getRbelWriter().serialize(input, new TigerJexlContext().withRootElement(input));
    } else {
      return RbelSerializationResult.withUnknownType(resolvedInput.getBytes(DEFAULT_CHARSET));
    }
  }

  private static RbelWriter getRbelWriter() {
    assureRbelIsInitialized();
    return rbelWriter;
  }

  private static RbelConverter getRbelConverter() {
    assureRbelIsInitialized();
    return rbelLogger.getRbelConverter();
  }

  private static synchronized void assureRbelIsInitialized() {
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
}
