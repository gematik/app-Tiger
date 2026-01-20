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
package de.gematik.rbellogger.facets.http2;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.facets.http.RbelHttpHeaderFacet;
import java.util.Optional;
import lombok.Builder;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

/**
 * Facet describing a reconstructed HTTP/2 message.
 */
public record RbelHttp2MessageFacet(RbelElement streamId, RbelElement headers, RbelElement body,
                                    RbelElement trailers, RbelElement httpVersion) implements
    RbelFacet {

  /**
   * Create a new HTTP/2 message facet.
   *
   * @param streamId    the stream identifier element
   * @param headers     the header element
   * @param body        the body element
   * @param trailers    the trailing headers element
   * @param httpVersion the HTTP version element
   */
  @Builder(toBuilder = true)
  public RbelHttp2MessageFacet {
  }

  /**
   * Collect child elements for tree rendering.
   *
   * @return the child element map
   */
  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("streamId", streamId)
        .with("headers", headers)
        .with("body", body)
        .with("trailers", trailers)
        .with("httpVersion", httpVersion);
  }

  /**
   * Build a concise description of the HTTP/2 message.
   *
   * @param element the owning element
   * @return the short description when available
   */
  @Override
  public Optional<String> printShortDescription(RbelElement element) {
    val headerFacet = headers == null
        ? Optional.<RbelHttpHeaderFacet>empty()
        : headers.getFacet(RbelHttpHeaderFacet.class);
    val path = headerFacet.flatMap(f -> f.getCaseInsensitiveMatches(":path").findFirst())
        .map(RbelElement::getRawStringContent);
    val status = headerFacet.flatMap(f -> f.getCaseInsensitiveMatches(":status").findFirst())
        .map(RbelElement::getRawStringContent);
    val bodyText = body == null ? "" : body.getRawStringContent();
    val description = new StringBuilder("HTTP/2 ");
    path.ifPresent(p -> description.append(p).append(" "));
    status.ifPresent(s -> description.append("status ").append(s).append(" "));
    description.append("body '").append(StringUtils.abbreviate(bodyText, 30)).append("'");
    return Optional.of(description.toString().trim());
  }
}
