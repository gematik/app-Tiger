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
package de.gematik.test.tiger.zion.config;

import static de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition.PathMatchingResult.EMPTY_MATCH;
import static de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition.PathMatchingResult.NO_MATCH;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * A configuration class to define path matching criteria in a zion server. It defines a path to
 * match which may have path variables which will be assigned to the value of the actual request.
 * Additional criterions, as JEXL expressions can also be included.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@Builder
public class ZionRequestMatchDefinition {
  private String path;
  private String method;

  @Getter(
      AccessLevel.NONE) // Set to none so that no getter is generated for it, and the access is made
  // exclusively with extractAdditionalCriteria()
  @JsonProperty // JsonProperty is here to still be able to serialize the field
  // since in de.gematik.test.tiger.ZionServerType.performStartup it gets serialized into json
  // before being passed into the ZionApplication.
  @Builder.Default
  @TigerSkipEvaluation
  private List<String> additionalCriterions = new ArrayList<>();

  @SneakyThrows
  public PathMatchingResult matchPathVariables(
      RbelElement requestRbelMessage, TigerJexlContext context) {
    if (path == null) {
      // When no path is defined, we return a match.
      return EMPTY_MATCH;
    }

    String fullUrlFromRequest =
        requestRbelMessage
            .getFacet(RbelHttpRequestFacet.class)
            .map(f -> f.getPath().getRawStringContent())
            .orElse("");
    // If there is no remaining path yet, we take it from the request, otherwise we load what is
    // remaining from the context
    String pathFromRequest =
        (String)
            context
                .getOptional(TigerJexlContext.REMAINING_PATH_FROM_REQUEST)
                .orElseGet(() -> getPathFromFullUrl(fullUrlFromRequest));

    PathPattern pathPattern = PathPatternParser.defaultInstance.parse(path);

    PathPattern.PathRemainingMatchInfo matchInfo =
        pathPattern.matchStartOfPath(PathContainer.parsePath(pathFromRequest));

    if (matchInfo == null) {
      return NO_MATCH;
    }

    context.set(TigerJexlContext.REMAINING_PATH_FROM_REQUEST, matchInfo.getPathRemaining().value());
    Map<String, String> capturedVariables = matchInfo.getUriVariables();

    return new PathMatchingResult(true, capturedVariables);
  }

  @SneakyThrows
  private static String getPathFromFullUrl(String fullUrlFromRequest) {
    return new URI(fullUrlFromRequest).getPath();
  }

  public record PathMatchingResult(boolean doesItMatch, Map<String, String> capturedVariables) {
    public static final PathMatchingResult EMPTY_MATCH = new PathMatchingResult(true, Map.of());
    public static final PathMatchingResult NO_MATCH = new PathMatchingResult(false, Map.of());
  }

  public List<String> extractAdditionalCriteria() {
    List<String> additionalCriteria = new ArrayList<>(additionalCriterions);
    if (method != null) {
      additionalCriteria.add("message.method == '%s'".formatted(method));
    }
    return additionalCriteria;
  }
}
