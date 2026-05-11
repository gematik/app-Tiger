/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.*;
import de.gematik.test.tiger.lib.exception.ValidatorAssertionError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the multi-response validator support. In LDAP, a SEARCH_REQUEST can produce multiple
 * SEARCH_RESULT_ENTRY responses followed by a final SEARCH_RESULT_DONE. The validator's "current
 * response" methods should search across all responses so that assertions like "current response
 * contains node X" succeed if ANY of the responses fulfils the condition.
 */
class LdapMultiResponseValidatorTest extends AbstractRbelMessageValidatorTest {

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();
  }

  // ---------------------------------------------------------------------------
  // Helpers to build minimal RbelElements with child nodes accessible via rbelPath
  // ---------------------------------------------------------------------------

  /** Creates an RbelElement with a named child node holding a string value. */
  private static RbelElement buildElementWithChild(String childKey, String childValue) {
    val parent = new RbelElement(null, null);
    val child = RbelElement.wrap(null, parent, childValue);
    parent.addFacet(
        new RbelFacet() {
          @Override
          public RbelMultiMap<RbelElement> getChildElements() {
            return new RbelMultiMap<RbelElement>().with(childKey, child);
          }
        });
    return parent;
  }

  /**
   * Builds a request element, N response elements, pairs them all, and installs them in the
   * retriever as currentRequest/currentResponse/currentResponses.
   *
   * <p>Each response has a child named {@code key} with the corresponding value from {@code
   * responseValues}. The request has a child named "requestMarker" with value "req".
   */
  private void setUpMultiResponseScenario(String key, String... responseValues) {
    val request = buildElementWithChild("requestMarker", "req");
    request.addFacet(new RbelRequestFacet("SEARCH_REQUEST", true));

    List<RbelElement> responses = new ArrayList<>();
    for (String value : responseValues) {
      val response = buildElementWithChild(key, value);
      response.addFacet(new RbelResponseFacet("SEARCH_RESULT_ENTRY"));
      responses.add(response);
    }

    // Build the pairing facet
    val pair = new TracingMessagePairFacet(request, responses);
    pair.markAsResponseComplete();
    request.addOrReplaceFacet(pair);
    responses.forEach(r -> r.addOrReplaceFacet(pair));

    rbelMessageRetriever.setCurrentRequest(request);
    rbelMessageRetriever.setCurrentResponse(responses.get(0));
    rbelMessageRetriever.getCurrentResponses().clear();
    rbelMessageRetriever.getCurrentResponses().addAll(responses);
  }

  /** Single-response setup for backward-compatibility tests. */
  private void setUpSingleResponseScenario(String key, String value) {
    val request = buildElementWithChild("requestMarker", "req");
    request.addFacet(new RbelRequestFacet("BIND_REQUEST", false));

    val response = buildElementWithChild(key, value);
    response.addFacet(new RbelResponseFacet("BIND_RESPONSE"));

    val pair = new TracingMessagePairFacet(response, request);
    request.addOrReplaceFacet(pair);
    response.addOrReplaceFacet(pair);

    rbelMessageRetriever.setCurrentRequest(request);
    rbelMessageRetriever.setCurrentResponse(response);
    rbelMessageRetriever.getCurrentResponses().clear();
    rbelMessageRetriever.getCurrentResponses().add(response);
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("Single-response backward compatibility")
  class SingleResponse {

    @Test
    @DisplayName("findElementsInCurrentResponse finds element in the single response")
    void findsElementInSingleResponse() {
      setUpSingleResponseScenario("dn", "cn=Alice");
      val result = rbelMessageRetriever.findElementsInCurrentResponse("$.dn");
      assertThat(result).hasSize(1);
      assertThat(result.get(0).printValue()).hasValue("cn=Alice");
    }

    @Test
    @DisplayName("findElementsInCurrentResponse throws with original message for single response")
    void throwsOriginalMessageForSingleResponse() {
      setUpSingleResponseScenario("dn", "cn=Alice");
      assertThatThrownBy(() -> rbelMessageRetriever.findElementsInCurrentResponse("$.nonExistent"))
          .isInstanceOf(ValidatorAssertionError.class)
          .hasMessageContaining("Unable to find element in response for rbel path")
          .hasMessageNotContaining("any of");
    }

    @Test
    @DisplayName("findElementInCurrentResponse finds element in single response")
    void findSingleElementInSingleResponse() {
      setUpSingleResponseScenario("dn", "cn=Alice");
      val result = rbelMessageRetriever.findElementInCurrentResponse("$.dn");
      assertThat(result.printValue()).hasValue("cn=Alice");
    }

    @Test
    @DisplayName("findElementInCurrentResponse throws with original message for single response")
    void throwsOriginalMessageForSingleElementLookup() {
      setUpSingleResponseScenario("dn", "cn=Alice");
      assertThatThrownBy(() -> rbelMessageRetriever.findElementInCurrentResponse("$.nonExistent"))
          .isInstanceOf(ValidatorAssertionError.class)
          .hasMessageContaining("No node matching path '$.nonExistent'!");
    }

    @Test
    @DisplayName("findElementsInCurrentResponseOrEmpty returns empty for missing path")
    void orEmptyReturnsEmptyForSingleResponse() {
      setUpSingleResponseScenario("dn", "cn=Alice");
      val result = rbelMessageRetriever.findElementsInCurrentResponseOrEmpty("$.nonExistent");
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Multi-response: element found in one of the responses")
  class MultiResponseFound {

    @Test
    @DisplayName("findElementsInCurrentResponse aggregates elements across all responses")
    void aggregatesElementsAcrossResponses() {
      setUpMultiResponseScenario("dn", "cn=Alice", "cn=Bob", "cn=Charlie");
      val result = rbelMessageRetriever.findElementsInCurrentResponse("$.dn");
      assertThat(result).hasSize(3);
      assertThat(result.stream().map(e -> e.printValue().orElse("")).toList())
          .containsExactly("cn=Alice", "cn=Bob", "cn=Charlie");
    }

    @Test
    @DisplayName("findElementsInCurrentResponseOrEmpty returns elements from all responses")
    void orEmptyReturnsAllElements() {
      setUpMultiResponseScenario("dn", "cn=Alice", "cn=Bob");
      val result = rbelMessageRetriever.findElementsInCurrentResponseOrEmpty("$.dn");
      assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findElementInCurrentResponse returns first matching element across responses")
    void findSingleElementAcrossResponses() {
      setUpMultiResponseScenario("dn", "cn=Alice", "cn=Bob");
      val result = rbelMessageRetriever.findElementInCurrentResponse("$.dn");
      assertThat(result.printValue()).hasValue("cn=Alice");
    }

    @Test
    @DisplayName("findElementInCurrentResponse finds element only present in a later response")
    void findsElementInLaterResponse() {
      // "secret" key only exists in the second response
      val request = buildElementWithChild("requestMarker", "req");
      request.addFacet(new RbelRequestFacet("SEARCH_REQUEST", true));

      val response1 = buildElementWithChild("dn", "cn=Alice");
      response1.addFacet(new RbelResponseFacet("SEARCH_RESULT_ENTRY"));

      val response2 = buildElementWithChild("secret", "42");
      response2.addFacet(new RbelResponseFacet("SEARCH_RESULT_ENTRY"));

      val pair = new TracingMessagePairFacet(request, List.of(response1, response2));
      pair.markAsResponseComplete();
      request.addOrReplaceFacet(pair);
      response1.addOrReplaceFacet(pair);
      response2.addOrReplaceFacet(pair);

      rbelMessageRetriever.setCurrentRequest(request);
      rbelMessageRetriever.setCurrentResponse(response1);
      rbelMessageRetriever.getCurrentResponses().clear();
      rbelMessageRetriever.getCurrentResponses().addAll(List.of(response1, response2));

      // "$.secret" is only in response2
      val result = rbelMessageRetriever.findElementInCurrentResponse("$.secret");
      assertThat(result.printValue()).hasValue("42");
    }
  }

  @Nested
  @DisplayName("Multi-response: element not found in any response")
  class MultiResponseNotFound {

    @Test
    @DisplayName("findElementsInCurrentResponse throws with per-response mismatch notes")
    void throwsWithPerResponseMismatchNotes() {
      setUpMultiResponseScenario("dn", "cn=Alice", "cn=Bob", "cn=Charlie");

      assertThatThrownBy(() -> rbelMessageRetriever.findElementsInCurrentResponse("$.nonExistent"))
          .isInstanceOf(ValidatorAssertionError.class)
          .hasMessageContaining("any of 3 responses")
          .satisfies(
              ex -> {
                val vea = (ValidatorAssertionError) ex;
                // One mismatch note per response
                assertThat(vea.getMismatchNotes()).hasSize(3);
                assertThat(allMismatchTypes(vea))
                    .allMatch(t -> t == RbelMismatchNoteFacet.MismatchType.MISSING_NODE);
              });
    }

    @Test
    @DisplayName("findElementInCurrentResponse throws with per-response mismatch notes")
    void findSingleElementThrowsWithPerResponseNotes() {
      setUpMultiResponseScenario("dn", "cn=Alice", "cn=Bob");

      assertThatThrownBy(() -> rbelMessageRetriever.findElementInCurrentResponse("$.nonExistent"))
          .isInstanceOf(ValidatorAssertionError.class)
          .hasMessageContaining("any of 2 responses")
          .satisfies(
              ex -> {
                val vea = (ValidatorAssertionError) ex;
                assertThat(vea.getMismatchNotes()).hasSize(2);
              });
    }

    @Test
    @DisplayName("findElementsInCurrentResponseOrEmpty returns empty for missing path across all")
    void orEmptyReturnsEmptyAcrossAll() {
      setUpMultiResponseScenario("dn", "cn=Alice", "cn=Bob");
      val result = rbelMessageRetriever.findElementsInCurrentResponseOrEmpty("$.nonExistent");
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("No current response set throws AssertionError")
    void noResponseSet() {
      rbelMessageRetriever.setCurrentResponse(null);
      assertThatThrownBy(() -> rbelMessageRetriever.findElementsInCurrentResponse("$.anything"))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("No current response message found");
    }

    @Test
    @DisplayName("Multi-response with two responses, element in both, returns all matches")
    void elementInBothResponses() {
      setUpMultiResponseScenario("status", "ok", "ok");
      val result = rbelMessageRetriever.findElementsInCurrentResponse("$.status");
      assertThat(result).hasSize(2);
    }
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  private List<RbelMismatchNoteFacet.MismatchType> allMismatchTypes(ValidatorAssertionError vea) {
    return vea.getMismatchNotes().values().stream()
        .flatMap(Collection::stream)
        .map(RbelMismatchNoteFacet::getMismatchType)
        .toList();
  }
}
