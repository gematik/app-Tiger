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
package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpHeaderFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class RbelHttpMessageWriter implements RbelElementWriter {

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelHttpResponseFacet.class)
        || oldTargetElement.hasFacet(RbelHttpRequestFacet.class);
  }

  @Override
  public RbelContent write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, RbelContent newContent) {
    final Optional<RbelHttpResponseFacet> responseFacet =
        oldTargetElement.getFacet(RbelHttpResponseFacet.class);
    final Optional<RbelHttpRequestFacet> requestFacet =
        oldTargetElement.getFacet(RbelHttpRequestFacet.class);
    final RbelHttpMessageFacet messageFacet =
        oldTargetElement.getFacetOrFail(RbelHttpMessageFacet.class);
    final StringJoiner joiner = new StringJoiner("\r\n");

    joiner.add(buildTitleLine(oldTargetModifiedChild, newContent, responseFacet, requestFacet));

    final boolean chunked = isChunkedMessage(oldTargetElement);
    final RbelContent body =
        applyChunkedEncoding(
            chunked,
            oldTargetElement.getElementCharset(),
            getBodyContent(messageFacet, oldTargetModifiedChild, newContent));
    if (messageFacet.getHeader() == oldTargetModifiedChild) {
      joiner.add(asString(newContent));
    } else {
      joiner.add(
          patchHeader(new String(messageFacet.getHeader().getRawContent()), body.size(), chunked));
    }
    joiner.add("");
    joiner.add("");
    return RbelContent.concat(
        RbelContent.of(joiner.toString(), oldTargetModifiedChild.getElementCharset()), body);
  }

  private RbelContent applyChunkedEncoding(boolean chunked, Charset charset, RbelContent body) {
    if (!chunked) {
      return body;
    }
    if (body.size() == 0) {
      return RbelContent.of("0\r\n\r\n", charset);
    }
    return RbelContent.concat(
        RbelContent.of(Integer.toHexString(body.size()) + "\r\n", charset),
        body,
        RbelContent.of("\r\n0\r\n\r\n", charset));
  }

  private static String asString(RbelContent content) {
    return new String(content.toByteArray());
  }

  private boolean isChunkedMessage(RbelElement oldTargetElement) {
    return oldTargetElement
        .getFacet(RbelHttpMessageFacet.class)
        .map(RbelHttpMessageFacet::getHeader)
        .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
        .stream()
        .flatMap(h -> h.getCaseInsensitiveMatches("Transfer-Encoding"))
        .map(RbelElement::getRawStringContent)
        .anyMatch(value -> value.equalsIgnoreCase("chunked"));
  }

  /**
   * The body is written decoded, so the content encoding is dropped. A chunked message stays
   * chunked and has no length of its own, any other body is given its length.
   */
  private String patchHeader(String headerRaw, int length, boolean chunked) {
    return Arrays.stream(headerRaw.split("\r\n"))
        .map(
            headerLine -> {
              if (headerLine.toLowerCase(Locale.ROOT).startsWith("content-length")) {
                return chunked ? null : "Content-Length: " + length;
              } else if (headerLine.toLowerCase(Locale.ROOT).startsWith("content-encoding")) {
                return null;
              } else if (headerLine.toLowerCase(Locale.ROOT).startsWith("transfer-encoding")) {
                return chunked ? headerLine : null;
              } else {
                return headerLine;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.joining("\r\n"));
  }

  private RbelContent getBodyContent(
      RbelHttpMessageFacet messageFacet,
      RbelElement oldTargetModifiedChild,
      RbelContent newContent) {
    if (messageFacet.getBody() == oldTargetModifiedChild) {
      return newContent;
    } else {
      return messageFacet.getBody().getContent();
    }
  }

  private String getResponseCode(
      Optional<RbelHttpResponseFacet> responseFacet,
      RbelElement oldTargetModifiedChild,
      RbelContent newContent) {
    if (responseFacet.isEmpty()) {
      return "200";
    }
    if (responseFacet.get().getResponseCode() == oldTargetModifiedChild) {
      return asString(newContent);
    } else {
      return responseFacet.get().getResponseCode().getRawStringContent();
    }
  }

  private Optional<RbelContent> getReasonPhrase(
      Optional<RbelHttpResponseFacet> responseFacet,
      RbelElement oldTargetModifiedChild,
      RbelContent newContent) {
    if (responseFacet.isEmpty() || responseFacet.get().getReasonPhrase() == null) {
      return Optional.empty();
    }
    if (responseFacet.get().getReasonPhrase() == oldTargetModifiedChild) {
      return Optional.of(newContent);
    } else {
      return Optional.of(responseFacet.get().getReasonPhrase().getContent())
          .filter(content -> !content.isNull());
    }
  }

  private String buildTitleLine(
      RbelElement oldTargetModifiedChild,
      RbelContent newContent,
      Optional<RbelHttpResponseFacet> responseFacet,
      Optional<RbelHttpRequestFacet> requestFacet) {
    StringBuilder builder = new StringBuilder();

    String request = buildRequest(oldTargetModifiedChild, newContent, requestFacet, builder);
    if (request != null) {
      return request;
    }
    return "HTTP/1.1 "
        + getResponseCode(responseFacet, oldTargetModifiedChild, newContent)
        + getReasonPhrase(responseFacet, oldTargetModifiedChild, newContent)
            .map(RbelContent::toReadableString)
            .filter(phrase -> !phrase.trim().isEmpty())
            .map(phrase -> " " + phrase)
            .orElse("");
  }

  private String buildRequest(
      RbelElement oldTargetModifiedChild,
      RbelContent newContent,
      Optional<RbelHttpRequestFacet> requestFacet,
      StringBuilder builder) {
    if (requestFacet.isPresent()) {
      if (requestFacet.get().getMethod() == oldTargetModifiedChild) {
        builder.append(asString(newContent));
      } else {
        builder.append(requestFacet.get().getMethod().getRawStringContent());
      }
      builder.append(" ");
      if (requestFacet.get().getPath() == oldTargetModifiedChild) {
        builder.append(asString(newContent));
      } else {
        builder.append(requestFacet.get().getPath().getRawStringContent());
      }
      builder.append(" HTTP/1.1");
      return builder.toString();
    }
    return null;
  }
}
