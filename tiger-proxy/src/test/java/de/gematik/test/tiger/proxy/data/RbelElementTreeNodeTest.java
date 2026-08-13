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
package de.gematik.test.tiger.proxy.data;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RbelElementTreeNodeTest {

  private static RbelElement parseJsonResponseBody(String jsonBody) {
    final String message =
        "HTTP/1.1 200\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: "
            + jsonBody.getBytes(StandardCharsets.UTF_8).length
            + "\r\n"
            + "\r\n"
            + jsonBody;
    return RbelLogger.build()
        .getRbelConverter()
        .convertElement(message.getBytes(StandardCharsets.UTF_8), null);
  }

  @Test
  void from_leafElement_shouldContainKeyAndFullContent() {
    final String longValue = "x".repeat(80);
    // string values are wrapped in a nested ".content" element; the node with key "leaf" is its
    // parent
    final RbelElement leaf =
        parseJsonResponseBody("{\"leaf\":\"" + longValue + "\"}")
            .findElement("$.body.leaf")
            .orElseThrow()
            .getParentNode();

    final RbelElementTreeNode node = RbelElementTreeNode.from(leaf);

    assertThat(node.key()).isEqualTo("leaf");
    assertThat(RbelOptions.getRbelPathTreeViewValueOutputLength())
        .as("test assumes the default (short) tree-view abbreviation length")
        .isLessThan(longValue.length());
    assertThat(node.content()).isEqualTo(longValue).doesNotEndWith("...");
  }

  @Test
  void from_containerElement_shouldContainNestedChildren() {
    final RbelElement parent =
        parseJsonResponseBody("{\"parent\":{\"childA\":\"1\",\"childB\":\"2\"}}")
            .findElement("$.body.parent")
            .orElseThrow();

    final RbelElementTreeNode node = RbelElementTreeNode.from(parent);

    assertThat(node.key()).isEqualTo("parent");
    assertThat(node.children())
        .extracting(RbelElementTreeNode::key)
        .containsExactlyInAnyOrder("childA", "childB");
  }

  @Test
  void from_shouldNotExposeStructuralFacets() {
    final RbelElement parent =
        parseJsonResponseBody("{\"parent\":{\"childA\":\"1\"}}")
            .findElement("$.body.parent")
            .orElseThrow();

    final RbelElementTreeNode node = RbelElementTreeNode.from(parent);

    assertThat(node.facets())
        .doesNotContain("RbelRootFacet", "RbelListFacet", "RbelNestedFacet", "RbelMapFacet");
  }
}
