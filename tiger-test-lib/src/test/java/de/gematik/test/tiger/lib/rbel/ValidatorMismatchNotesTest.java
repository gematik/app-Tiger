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
package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelMismatchNoteFacet;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.lib.exception.ValidatorAssertionError;
import java.util.Collection;
import java.util.List;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidatorMismatchNotesTest extends AbstractRbelMessageValidatorTest {

  @BeforeEach
  void init() {
    super.setUp();
  }

  @Test
  @DisplayName("Path mismatch should yield WRONG_PATH notes for all candidate messages")
  void pathMismatch_yieldsWrongPathMismatchNotes() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();

    val requestParameter = RequestParameter.builder().path("/NOWAY.*").build();

    assertThatThrownBy(() -> rbelMessageRetriever.filterRequestsAndStoreInContext(requestParameter))
        .isInstanceOf(ValidatorAssertionError.class)
        .satisfies(
            ex -> {
              var vea = (ValidatorAssertionError) ex;
              assertThat(vea.getMismatchNotes()).hasSizeGreaterThanOrEqualTo(2);
              assertThat(allMismatchTypes(vea))
                  .allMatch(t -> t == RbelMismatchNoteFacet.MismatchType.WRONG_PATH);
            });
  }

  @Test
  @DisplayName("Value mismatch should yield VALUE_MISMATCH notes for involved nodes")
  void valueMismatch_yieldsValueMismatchNotes() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();

    val requestParameter =
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonalagentXXXX")
            .build();

    assertThatThrownBy(() -> rbelMessageRetriever.filterRequestsAndStoreInContext(requestParameter))
        .isInstanceOf(ValidatorAssertionError.class)
        .satisfies(
            ex -> {
              var vea = (ValidatorAssertionError) ex;
              assertThat(vea.getMismatchNotes()).isNotEmpty();
              assertThat(allMismatchTypes(vea))
                  .allMatch(t -> t == RbelMismatchNoteFacet.MismatchType.VALUE_MISMATCH);
            });
  }

  @Test
  @DisplayName("Missing node should yield MISSING_NODE notes for candidate messages")
  void missingNode_yieldsMissingNodeNotes() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();

    val requestParameter =
        RequestParameter.builder().path(".*").rbelPath("$.header.User-AgentXXX").build();

    assertThatThrownBy(() -> rbelMessageRetriever.filterRequestsAndStoreInContext(requestParameter))
        .isInstanceOf(ValidatorAssertionError.class)
        .satisfies(
            ex -> {
              var vea = (ValidatorAssertionError) ex;
              assertThat(vea.getMismatchNotes()).isNotEmpty();
              assertThat(allMismatchTypes(vea))
                  .allMatch(t -> t == RbelMismatchNoteFacet.MismatchType.MISSING_NODE);
            });
  }

  @Test
  @DisplayName("Ambiguous path selection when exactly-one expected yields AMBIGUOUS note")
  void ambiguousNodeSelection_yieldsAmbiguousNote() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    // pick a request that has multiple '$..td.a.text' nodes in its response body
    RBelValidatorGlue gluecode = new RBelValidatorGlue(rbelMessageRetriever);
    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");

    assertThatThrownBy(() -> rbelMessageRetriever.findElementInCurrentResponse("$..td.a.text"))
        .isInstanceOf(ValidatorAssertionError.class)
        .satisfies(
            ex -> {
              var vea = (ValidatorAssertionError) ex;
              assertThat(allMismatchTypes(vea))
                  .contains(RbelMismatchNoteFacet.MismatchType.AMBIGUOUS);
            });
  }

  @Test
  @DisplayName("Unknown node lookup yields MISSING_NODE note")
  void unknownNodeLookup_yieldsMissingNodeNote() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RBelValidatorGlue gluecode = new RBelValidatorGlue(rbelMessageRetriever);
    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");

    assertThatThrownBy(() -> rbelMessageRetriever.findElementInCurrentResponse("$..doesNotExist"))
        .isInstanceOf(ValidatorAssertionError.class)
        .satisfies(
            ex -> {
              var vea = (ValidatorAssertionError) ex;
              assertThat(allMismatchTypes(vea))
                  .containsOnly(RbelMismatchNoteFacet.MismatchType.MISSING_NODE);
            });
  }

  @Test
  @DisplayName("Mixed missing node and value mismatch notes are reported together")
  void mixedMissingNodeAndValueMismatchNotes() {
    // First request (no Eitzen-Specific-header) -> MISSING_NODE, second has header with a value we
    // intentionally mismatch
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();

    val requestParameter =
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.Eitzen-Specific-header")
            .value("THIS_VALUE_WILL_NOT_MATCH")
            .build();

    assertThatThrownBy(() -> rbelMessageRetriever.filterRequestsAndStoreInContext(requestParameter))
        .isInstanceOf(ValidatorAssertionError.class)
        .satisfies(
            ex -> {
              var vea = (ValidatorAssertionError) ex;
              var types = allMismatchTypes(vea);
              assertThat(types)
                  .contains(
                      RbelMismatchNoteFacet.MismatchType.MISSING_NODE,
                      RbelMismatchNoteFacet.MismatchType.VALUE_MISMATCH);
            });
  }

  @Test
  @DisplayName("Internal exception while resolving path yields UNKNOWN mismatch note")
  void unknownMismatchNoteProduced() {
    // Prepare a response element whose path lookup throws a RuntimeException so the catch block
    // creates UNKNOWN note
    RbelElement faultyResponse = spy(new RbelElement());
    doThrow(new RuntimeException("boom")).when(faultyResponse).findRbelPathMembers(anyString());
    rbelMessageRetriever.setCurrentResponse(faultyResponse);

    assertThatThrownBy(() -> rbelMessageRetriever.findElementInCurrentResponse("$.anything"))
        .isInstanceOf(ValidatorAssertionError.class)
        .satisfies(
            ex -> {
              var vea = (ValidatorAssertionError) ex;
              assertThat(allMismatchTypes(vea))
                  .containsOnly(RbelMismatchNoteFacet.MismatchType.UNKNOWN);
            });
  }

  private List<RbelMismatchNoteFacet.MismatchType> allMismatchTypes(ValidatorAssertionError vea) {
    return vea.getMismatchNotes().values().stream()
        .flatMap(Collection::stream)
        .map(RbelMismatchNoteFacet::getMismatchType)
        .toList();
  }
}
