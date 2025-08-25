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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.RbelSerializationResult;
import de.gematik.test.tiger.RbelLoggerWriter;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibConfig;
import de.gematik.test.tiger.lib.rbel.ModeType;
import io.cucumber.java.ParameterType;
import io.restassured.http.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

/**
 * contains ParameterType definitions for cucumber parameters. The method name is used as name of
 * the parameter type in cucumber steps. The regex is used to check if the parameter value matches
 * using Regex. All parameter types do support resolution using the TigerGlobalConfiguration.
 *
 * @see TigerGlobalConfiguration#resolvePlaceholders(String)
 * @see <a href="https://github.com/cucumber/cucumber-expressions">Cucumber expressions</a>
 */
public class TigerParameterTypeDefinitions {
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  private static final String STRING_LITERAL = "\"[^\"]*\"|'[^']*'";

  private static RbelLoggerWriter rbelLoggerWriter;
  // THe type names are defined as the names of the methods. It is therefore necessary to manually
  // add to this list any
  // new resolved variable type that is added to this class.
  private static final List<String> resolvedVariableTypeNames =
      List.of("tigerResolvedString", "tigerResolvedUrl");

  /**
   * checks if the given typeName is a resolved variable type <br>
   *
   * @param typeName the name of the type
   * @return true if the typeName is a resolved variable type
   */
  public static boolean isResolvedVariableType(String typeName) {
    return resolvedVariableTypeNames.contains(typeName);
  }

  protected TigerParameterTypeDefinitions() {}

  /**
   * @param name name of the HTTP request method
   * @return an actual {@link Method}
   */
  @ParameterType("GET|POST|DELETE|PUT|OPTIONS|" + STRING_LITERAL)
  public static Method requestType(String name) {
    return Method.valueOf(resolveToString(name));
  }

  /**
   * @param mode type of data format.
   * @return an actual {@link ModeType}
   */
  @ParameterType("XML|JSON|" + STRING_LITERAL)
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
  @ParameterType(STRING_LITERAL)
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
  @ParameterType(STRING_LITERAL)
  public static @NotNull URI tigerResolvedUrl(String address) {
    return new URI(resolveToString(address));
  }

  /**
   * replaces String values with its enum value in {@link RbelContentType}
   *
   * @param value string value in enum
   * @return Enum value
   */
  @ParameterType("XML|JSON|JWE|JWT|BEARER_TOKEN|URL|" + STRING_LITERAL)
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
    synchronized (TigerParameterTypeDefinitions.class) {
      if (rbelLoggerWriter == null) {
        rbelLoggerWriter = new RbelLoggerWriter();
      }
    }
    final String resolvedInput = TigerGlobalConfiguration.resolvePlaceholders(value);
    TigerLibConfig libConfig = TigerDirector.getLibConfig();
    if (libConfig != null && libConfig.getHttpClientConfig().isActivateRbelWriter()) {
      final RbelElement input =
          rbelLoggerWriter.getRbelConverter().convertElement(resolvedInput, null);
      return rbelLoggerWriter
          .getRbelWriter()
          .serialize(input, new TigerJexlContext().withRootElement(input));
    } else {
      return RbelSerializationResult.withUnknownType(resolvedInput.getBytes(DEFAULT_CHARSET));
    }
  }
}
