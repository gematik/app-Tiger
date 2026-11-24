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
package de.gematik.rbellogger.data;

import static de.gematik.rbellogger.TestUtils.readAndConvertCurlMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.util.RbelContent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
@Tag("de.gematik.test.tiger.common.SmokeTest")
class RbelElementTest {
  private static final RbelElement msg;

  static {
    try {
      msg = readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void theSearchForAKeyOnlyEntersTheSubtreesThatTheCallerAccepts() {
    var top = new RbelElement(new byte[0], null);
    var child = new RbelElement(new byte[0], top);
    var keys = new RbelElement(new byte[0], child);
    child.addFacet(new RbelMapFacet(new RbelMultiMap<RbelElement>().with("keys", keys)));
    top.addFacet(new RbelMapFacet(new RbelMultiMap<RbelElement>().with("child", child)));

    assertThat(top.findAllInTreeWithKey("keys")).containsExactly(keys);
    assertThat(top.findAllInTreeWithKey("keys", element -> element != child)).isEmpty();
    assertThat(top.findAllInTreeWithKey("child", element -> false)).containsExactly(child);
  }

  @Test
  void facetMetadataListeners_areNotifiedThroughTheRootOfTheTree() {
    var root = new RbelElement(new byte[0], null);
    var child = new RbelElement(new byte[0], root);
    var notified = new java.util.concurrent.atomic.AtomicInteger();
    Runnable listener = notified::incrementAndGet;
    root.addFacetMetadataUpdateListener(listener);

    child.addFacet(new de.gematik.rbellogger.data.core.RbelBinaryFacet());
    root.removeFacetMetadataUpdateListener(listener);
    child.addFacet(new de.gematik.rbellogger.data.core.RbelNoteFacet("x"));

    assertThat(notified).hasValue(1);
  }

  @Test
  void renameElementViaBuilder_UuidShouldChange() {
    RbelElement originalElement = new RbelElement("fo".getBytes(), null);
    RbelElement renamedElement = originalElement.toBuilder().uuid("another uuid").build();

    assertThat(originalElement.getUuid()).isNotEqualTo(renamedElement.getUuid());
  }

  @Test
  void duplicatedElementViaBuilder_UuidShouldNotChange() {
    RbelElement originalElement = new RbelElement("fo".getBytes(), null);
    RbelElement renamedElement = originalElement.toBuilder().build();

    assertThat(originalElement.getUuid()).isEqualTo(renamedElement.getUuid());
  }

  @Test
  void phaseChangedInRootNode_shouldBeVisibleForAllNodes() {
    msg.setConversionPhase(RbelConversionPhase.DELETED);
    assertThat(msg.findRbelPathMembers("$.."))
        .allMatch(el -> el.getConversionPhase() == RbelConversionPhase.DELETED);
  }

  @Test
  void setUsedBytes_ShouldSetUsedBytesCorrectly() {
    RbelElement element = new RbelElement(RbelContent.of(new byte[] {1, 2, 3, 4, 5}));

    element.setUsedBytes(3);

    assertThat(element.getSize()).isEqualTo(3);
    assertThat(element.getContent().toByteArray()).containsExactly(1, 2, 3);
  }

  @Test
  void setUsedBytes_ShouldThrowException_WhenUsedBytesExceedsContentSize() {
    RbelElement element = new RbelElement(RbelContent.of(new byte[] {1, 2, 3}));

    assertThatThrownBy(() -> element.setUsedBytes(5))
        .isInstanceOf(IndexOutOfBoundsException.class)
        .hasMessageContaining("Invalid size: 5, current size: 3");
  }

  @Test
  void setUsedBytes_ShouldThrowException_WhenUsedBytesIsNegative() {
    RbelElement element = new RbelElement(RbelContent.of(new byte[] {1, 2, 3}));

    assertThatThrownBy(() -> element.setUsedBytes(-1))
        .isInstanceOf(IndexOutOfBoundsException.class)
        .hasMessageContaining("Invalid size: -1, current size: 3");
  }

  @Test
  void setUsedBytes_ShouldHandleZeroUsedBytes() {
    RbelElement element = new RbelElement(RbelContent.of(new byte[] {1, 2, 3}));

    element.setUsedBytes(0);

    assertThat(element.getSize()).isZero();
  }

  @Test
  void getChildNodes_listsTheSameElementsAsTheStream() {
    final List<RbelElement> everyNode = new ArrayList<>(List.of(msg));
    for (int i = 0; i < everyNode.size(); i++) {
      everyNode.addAll(everyNode.get(i).getChildNodesStream().toList());
    }

    assertThat(everyNode).hasSizeGreaterThan(50);
    for (RbelElement node : everyNode) {
      assertThat(node.getChildNodes())
          .containsExactlyElementsOf(node.getChildNodesStream().toList());
    }
  }

  @Test
  void setUsedBytes_ShouldHandleFullContentUsage() {
    RbelElement element = new RbelElement(RbelContent.of(new byte[] {1, 2, 3}));

    element.setUsedBytes(3);

    assertThat(element.getSize()).isEqualTo(3);
    assertThat(element.getContent().toByteArray()).containsExactly(1, 2, 3);
  }
}
