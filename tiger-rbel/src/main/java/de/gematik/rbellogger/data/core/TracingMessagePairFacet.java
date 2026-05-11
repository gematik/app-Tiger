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
package de.gematik.rbellogger.data.core;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

/**
 * Facet that pairs a request with one or more responses.
 *
 * <p>Both request and every response carry the same facet instance, so all participants share the
 * same ordered list of responses.
 *
 * <p>{@link #getOtherMessage(RbelElement)} navigates cyclically: request → response[0] →
 * response[1] → … → response[N-1] → request, which is used by the WebUI partner-message button.
 */
@Data
@Builder(toBuilder = true)
public class TracingMessagePairFacet implements RbelFacet {

  private final RbelElement request;

  /** Ordered list of all responses paired with this request. Never null, may be empty. */
  @Builder.Default private final List<RbelElement> responses = new ArrayList<>();

  /**
   * Convenience constructor for the common single-response case. This keeps backward compatibility
   * with existing callers that do {@code new TracingMessagePairFacet(response, request)}.
   */
  public TracingMessagePairFacet(RbelElement response, RbelElement request) {
    this.request = request;
    this.responses = new ArrayList<>();
    if (response != null) {
      this.responses.add(response);
    }
  }

  /** Constructor for multi-response pairing. */
  public TracingMessagePairFacet(RbelElement request, List<RbelElement> responses) {
    this.request = request;
    this.responses = responses != null ? new ArrayList<>(responses) : new ArrayList<>();
  }

  /**
   * Returns the first (or only) response, for backward compatibility.
   *
   * @return the first response or {@code null} if there are no responses
   */
  public RbelElement getResponse() {
    return responses.isEmpty() ? null : responses.get(0);
  }

  /**
   * Returns an unmodifiable view of all responses paired with this request.
   *
   * @return list of responses (never null, may be empty)
   */
  public List<RbelElement> getResponses() {
    return Collections.unmodifiableList(responses);
  }

  /**
   * Adds an additional response to this pairing. This mutates the shared list so that all
   * participants (request + all existing responses) immediately see the new response.
   *
   * @param response the response to add
   */
  public void addResponse(RbelElement response) {
    if (response != null && !responses.contains(response)) {
      responses.add(response);
    }
  }

  /**
   * Returns whether the full set of responses has arrived. For single-response protocols this is
   * always {@code true}. For multi-response protocols this starts as {@code false} and becomes
   * {@code true} once the terminal response has been paired.
   */
  public boolean isResponseComplete() {
    return request == null || !request.hasFacet(RbelResponseIncompleteFacet.class);
  }

  /** Marks the response sequence as incomplete by adding a marker facet to the request. */
  public void markAsResponseIncomplete() {
    if (request == null) {
      throw new RbelConversionException("Cannot mark response as incomplete: request is null");
    }
    if (!request.hasFacet(RbelResponseIncompleteFacet.class)) {
      request.addFacet(new RbelResponseIncompleteFacet());
    }
  }

  /** Marks the response sequence as complete by removing the marker facet from the request. */
  public void markAsResponseComplete() {
    if (request != null) {
      request.removeFacetsOfType(RbelResponseIncompleteFacet.class);
    }
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }

  /**
   * Cyclic navigation: request → first response → second response → … → last response → request.
   *
   * <p>For a request, returns the first response. For a response, returns the next response in the
   * list, or the request if it is the last response.
   */
  public Optional<RbelElement> getOtherMessage(RbelElement thisMessage) {
    if (thisMessage.equals(request)) {
      return responses.isEmpty() ? Optional.empty() : Optional.of(responses.get(0));
    }
    int idx = responses.indexOf(thisMessage);
    if (idx >= 0) {
      if (idx + 1 < responses.size()) {
        // navigate to next response
        return Optional.of(responses.get(idx + 1));
      } else {
        // last response → navigate back to request
        return Optional.ofNullable(request);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns all paired messages as an ordered list: [request, response0, response1, …].
   *
   * @return ordered list of all messages in this pairing group
   */
  public List<RbelElement> getAllPairedMessages() {
    List<RbelElement> result = new ArrayList<>();
    if (request != null) {
      result.add(request);
    }
    result.addAll(responses);
    return Collections.unmodifiableList(result);
  }

  /**
   * Returns all paired messages except the given one, in order: [request, response0, response1, …]
   * minus {@code thisMessage}.
   *
   * @param thisMessage the message to exclude from the result
   * @return ordered list of all other messages in this pairing group
   */
  public List<RbelElement> getOtherMessages(RbelElement thisMessage) {
    return getAllPairedMessages().stream().filter(m -> m != thisMessage).toList();
  }

  @Override
  public void waitForFacetToHaveParsedPartners(RbelElement rbelElement, RbelConverter converter) {
    if (request != null && !request.equals(rbelElement)) {
      converter.waitForGivenElementToBeParsed(request);
    }
    for (RbelElement response : responses) {
      if (!response.equals(rbelElement)) {
        converter.waitForGivenElementToBeParsed(response);
      }
    }
  }
}
