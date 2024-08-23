/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;

public class RbelHttpResponseWriter implements RbelElementWriter {

  @Override
  public boolean canWrite(RbelElement oldTargetElement) {
    return oldTargetElement.hasFacet(RbelHttpResponseFacet.class)
        || oldTargetElement.hasFacet(RbelHttpRequestFacet.class);
  }

  @Override
  public byte[] write(
      RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
    final Optional<RbelHttpResponseFacet> responseFacet =
        oldTargetElement.getFacet(RbelHttpResponseFacet.class);
    final Optional<RbelHttpRequestFacet> requestFacet =
        oldTargetElement.getFacet(RbelHttpRequestFacet.class);
    final RbelHttpMessageFacet messageFacet =
        oldTargetElement.getFacetOrFail(RbelHttpMessageFacet.class);
    final StringJoiner joiner = new StringJoiner("\r\n");

    joiner.add(buildTitleLine(oldTargetModifiedChild, newContent, responseFacet, requestFacet));

    byte[] body =
        getChunkedMapper(oldTargetElement)
            .apply(getBodyContent(messageFacet, oldTargetModifiedChild, newContent));
    if (messageFacet.getHeader() == oldTargetModifiedChild) {
      joiner.add(new String(newContent));
    } else {
      joiner.add(patchHeader(new String(messageFacet.getHeader().getRawContent()), body.length));
    }
    joiner.add("");
    joiner.add("");
    return ArrayUtils.addAll(
        joiner.toString().getBytes(oldTargetModifiedChild.getElementCharset()), body);
  }

  private UnaryOperator<byte[]> getChunkedMapper(RbelElement oldTargetElement) {
    if (isChunkedMessage(oldTargetElement)) {
      return array ->
          ArrayUtils.addAll(
              (array.length + "\r\n").getBytes(oldTargetElement.getElementCharset()),
              ArrayUtils.addAll(
                  array, ("\r\n0\r\n").getBytes(oldTargetElement.getElementCharset())));
    } else {
      return UnaryOperator.identity();
    }
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

  private String patchHeader(String headerRaw, int length) {
    return Arrays.stream(headerRaw.split("\r\n"))
        .map(
            headerLine -> {
              if (headerLine.toLowerCase(Locale.ROOT).startsWith("content-length")) {
                return "Content-Length: " + length;
              } else if (headerLine.toLowerCase(Locale.ROOT).startsWith("content-encoding")) {
                return null;
              } else if (headerLine.toLowerCase(Locale.ROOT).startsWith("transfer-encoding")) {
                return null;
              } else {
                return headerLine;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.joining("\r\n"));
  }

  private byte[] getBodyContent(
      RbelHttpMessageFacet messageFacet, RbelElement oldTargetModifiedChild, byte[] newContent) {
    if (messageFacet.getBody() == oldTargetModifiedChild) {
      return newContent;
    } else {
      return messageFacet.getBody().getRawContent();
    }
  }

  private String getResponseCode(
      Optional<RbelHttpResponseFacet> responseFacet,
      RbelElement oldTargetModifiedChild,
      byte[] newContent) {
    if (responseFacet.isEmpty()) {
      return "200";
    }
    if (responseFacet.get().getResponseCode() == oldTargetModifiedChild) {
      return new String(newContent);
    } else {
      return responseFacet.get().getResponseCode().getRawStringContent();
    }
  }

  private Optional<byte[]> getReasonPhrase(
      Optional<RbelHttpResponseFacet> responseFacet,
      RbelElement oldTargetModifiedChild,
      byte[] newContent) {
    if (responseFacet.isEmpty() || responseFacet.get().getReasonPhrase() == null) {
      return Optional.empty();
    }
    if (responseFacet.get().getReasonPhrase() == oldTargetModifiedChild) {
      return Optional.of(newContent);
    } else {
      return Optional.ofNullable(responseFacet.get().getReasonPhrase().getRawContent());
    }
  }

  private String buildTitleLine(
      RbelElement oldTargetModifiedChild,
      byte[] newContent,
      Optional<RbelHttpResponseFacet> responseFacet,
      Optional<RbelHttpRequestFacet> requestFacet) {
    StringBuilder builder = new StringBuilder();

    String request =
        buildRequest(oldTargetModifiedChild, new String(newContent), requestFacet, builder);
    if (request != null) {
      return request;
    }
    String responseCodeContent = getResponseCode(responseFacet, oldTargetModifiedChild, newContent);

    final Optional<byte[]> reasonPhrase =
        getReasonPhrase(responseFacet, oldTargetModifiedChild, newContent);
    if (reasonPhrase.isPresent() && new String(reasonPhrase.get()).trim().length() > 0) {
      String reasonPhraseContent = " " + new String(reasonPhrase.get(), StandardCharsets.UTF_8);
      return "HTTP/1.1 " + responseCodeContent + reasonPhraseContent;
    }

    return "HTTP/1.1 " + responseCodeContent;
  }

  private String buildRequest(
      RbelElement oldTargetModifiedChild,
      String newContent,
      Optional<RbelHttpRequestFacet> requestFacet,
      StringBuilder builder) {
    if (requestFacet.isPresent()) {
      if (requestFacet.get().getMethod() == oldTargetModifiedChild) {
        builder.append(newContent);
      } else {
        builder.append(requestFacet.get().getMethod().getRawStringContent());
      }
      builder.append(" ");
      if (requestFacet.get().getPath() == oldTargetModifiedChild) {
        builder.append(newContent);
      } else {
        builder.append(requestFacet.get().getPath().getRawStringContent());
      }
      builder.append(" HTTP/1.1");
      return builder.toString();
    }
    return null;
  }
}
