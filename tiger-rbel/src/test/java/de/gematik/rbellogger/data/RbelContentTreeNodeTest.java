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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.builder.RbelBuilder;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import de.gematik.rbellogger.writer.RbelContentTreeConverter;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.rbellogger.writer.tree.RbelContentTreeNode;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelContentTreeNodeTest {

  private RbelContentTreeNode msg1;
  private RbelContentTreeNode msg2;

  private RbelContentTreeNode msg3;

  static String jsonTest =
      """
      {
          "blub": {
              "foo":"bar"
          }
      }
      """;

  static String jsonTest2 =
      """
      {
          "blub": {
              "foo":"bar",
              "foo": {
                  "some": "value"
                  }
          },
          "blab": {
              "foo":"bar"
          }
      }
      """;

  static String xmlTest = "<blub><foo>bar</foo></blub>";
  private RbelConverter converter;

  @BeforeEach
  void setup() {
    converter = RbelLogger.build().getRbelConverter();

    final RbelElement input1 = new RbelElement(jsonTest.getBytes(StandardCharsets.UTF_8), null);
    converter.convertElement(input1);
    msg1 = new RbelContentTreeConverter(input1, new TigerJexlContext()).convertToContentTree();

    final RbelElement input2 = new RbelElement(jsonTest2.getBytes(StandardCharsets.UTF_8), null);
    converter.convertElement(input2);
    msg2 = new RbelContentTreeConverter(input2, new TigerJexlContext()).convertToContentTree();

    final RbelElement input3 = new RbelElement(xmlTest.getBytes(StandardCharsets.UTF_8), null);
    converter.convertElement(input3);
    msg3 = new RbelContentTreeConverter(input3, new TigerJexlContext()).convertToContentTree();
  }

  @Test
  void getFirstTest() {
    assertThat(msg1.getFirst("blub")).isPresent();
  }

  @Test
  void getFirstFailTest() {
    assertThat(msg1.getFirst("foo")).isEmpty();
  }

  @Test
  void getAllTest() {
    assertEquals(1, msg1.getFirst("blub").orElseThrow().getAll("foo").size());
  }

  @Test
  void getParentNodeTest() {
    assertNotNull(msg1.findElement("$.blub").orElseThrow().getParentNode());
    assertEquals(
        "blub",
        msg1.findElement("$.blub.foo").orElseThrow().getParentNode().getKey().orElseThrow());
  }

  @Test
  void getChildNodesTest() {
    assertEquals(1, msg1.getChildNodes().size());
  }

  @Test
  void getChildNodesWithKeyTest() {
    assertTrue(
        msg1.getChildNodesWithKey().stream().map(Map.Entry::getKey).toList().contains("blub"));
  }

  @Test
  void findElementTest() {
    assertEquals("bar", msg1.findElement("$.blub.foo").orElseThrow().getRawStringContent());
  }

  @Test
  void findElementFailTest() {
    assertTrue(msg1.findElement("$.blub.bar").isEmpty());
  }

  @Test
  void findRootElementTest() {
    assertEquals(
        2,
        msg2.findElement("$.blub.foo.some")
            .orElseThrow()
            .findRootElement()
            .getChildNodesWithKey()
            .size());
    assertTrue(
        msg2
            .findElement("$.blub.foo.some")
            .orElseThrow()
            .findRootElement()
            .getChildNodesWithKey()
            .stream()
            .allMatch(k -> (k.getKey().equals("blub") || k.getKey().equals("blab"))));
  }

  @Test
  void findNodePathTest() {
    assertEquals("blub.foo", msg2.findElement("$.blub.foo").orElseThrow().findNodePath());
  }

  @Test
  void findKeyInParentElementTest() {
    assertTrue(msg2.findKeyInParentElement().isEmpty());
    assertEquals(
        "foo", msg2.findElement("$.blub.foo").orElseThrow().findKeyInParentElement().orElseThrow());
  }

  @Test
  void getRawStringContentTest() {
    assertEquals(xmlTest, msg3.getRawStringContent());
  }

  @Test
  void getElementCharsetTest() {
    assertEquals(StandardCharsets.UTF_8, msg1.getElementCharset());
  }

  @Test
  void getKeyTest() {
    assertEquals("foo", msg2.findElement("$.blub.foo").orElseThrow().getKey().orElseThrow());
  }

  @Test
  void setRawStringContent() {
    msg1.findElement("$.blub.foo").orElseThrow().setRawStringContent("new String");
    assertEquals("new String", msg1.findElement("$.blub.foo").orElseThrow().getRawStringContent());
  }

  @Test
  void changeJsonRootNodeChildDirectly_shouldKeepUntouchedChildrenIntact() {
    final RbelElement input =
        converter.convertElement(
            """
            {
              "alice": "bob",
              "tom": "jerry"
            }
            """,
            null);
    val builder =
        new RbelBuilder(
            new RbelContentTreeConverter(input, new TigerJexlContext()).convertToContentTree());
    builder.setValueAt("$.alice", "munro");
    RbelElementAssertion.assertThat(converter.convertElement(builder.serialize(), null))
        .hasStringContentEqualToAtPosition("$.alice", "munro")
        .hasStringContentEqualToAtPosition("$.tom", "jerry");
  }

  @Test
  void replaceRootNodeChildDirectly() {
    final RbelElement input = converter.convertElement("{‘alice': 'bob'}", null);
    val builder =
        new RbelBuilder(
            new RbelContentTreeConverter(input, new TigerJexlContext()).convertToContentTree());
    builder.setValueAt("$.", "{'foo':'bar'}");
    RbelElementAssertion.assertThat(converter.convertElement(builder.serialize(), null))
        .hasStringContentEqualToAtPosition("$.foo", "bar")
        .doesNotHaveChildWithPath("$.alice");
  }

  @Test
  void testUpdateContentUpdatesParentChildrenMap() {
    // 1. Setup Parent and Child
    RbelContentTreeNode parent =
        new RbelContentTreeNode(new RbelMultiMap<>(), "parent".getBytes(StandardCharsets.UTF_8));
    RbelContentTreeNode child =
        new RbelContentTreeNode(new RbelMultiMap<>(), "<child/>".getBytes(StandardCharsets.UTF_8));

    child.setType(RbelContentType.XML);
    child.setKey("child");
    child.setParentNode(parent); // Link parent, but do NOT add to parent's children map

    // Validate precondition: Child is NOT known to parent
    assertThat(parent.getChildNodesWithKey().get("child")).isNull();

    // 2. Trigger updateContent
    // we change the child to trigger updateAncestorContent -> updateContent
    child.setChildNodes(new RbelMultiMap<>());

    // 3. Verify that the parent map was updated
    assertThat(parent.getChildNodesWithKey().get("child"))
        .as("Child SHOULD be added to parent map after updateContent()")
        .isNotNull()
        .isEqualTo(child);
  }

  @Test
  void shouldReplaceExistingChildCorrectly() {
    // START: Create a simple tree
    // root -> { "child1": "value1", "child2": "value2" }
    RbelContentTreeNode child1 =
        new RbelContentTreeNode(null, "value1".getBytes(StandardCharsets.UTF_8));
    RbelContentTreeNode child2 =
        new RbelContentTreeNode(null, "value2".getBytes(StandardCharsets.UTF_8));

    RbelMultiMap<RbelContentTreeNode> initialChildren = new RbelMultiMap<>();
    initialChildren.put("child1", child1);
    initialChildren.put("child2", child2);

    RbelContentTreeNode root =
        new RbelContentTreeNode(initialChildren, "root".getBytes(StandardCharsets.UTF_8));

    // Ensure initial state
    assertThat(root.getChildNodesWithKey().getAll("child1")).hasSize(1).contains(child1);
    assertThat(root.getChildNodesWithKey().getAll("child2")).hasSize(1).contains(child2);
    assertThat(child1.getParentNode()).isEqualTo(root);

    // START: Replacement
    RbelContentTreeNode newChild1 =
        new RbelContentTreeNode(null, "newValue1".getBytes(StandardCharsets.UTF_8));
    root.addOrReplaceChild("child1", newChild1);

    // VERIFY: Old child is gone, new child is present
    assertThat(root.getChildNodesWithKey().getAll("child1"))
        .hasSize(1)
        .contains(newChild1)
        .doesNotContain(child1);

    // VERIFY: Sibling is unaffected
    assertThat(root.getChildNodesWithKey().getAll("child2")).hasSize(1).contains(child2);

    // VERIFY: Parent linkage
    assertThat(newChild1.getParentNode()).isEqualTo(root);
    assertThat(newChild1.getKey()).hasValue("child1");

    // VERIFY: updateContent() was called and worked (serialization updated)
    // Note: Since we constructed the tree manually and didn't set a specific type (XML/JSON),
    // serialization might depend on defaults or need explicit type setting for exact format check.
    // But it should at least contain the new value.
    assertThat(root.getChildNodesWithKey().getAll("child1").get(0).getRawStringContent())
        .as("Root content should be updated via updateContent()")
        .contains("newValue1")
        .doesNotContain("value1");
  }

  @Test
  void shouldAddNewChildIfKeyMissing() {
    RbelContentTreeNode root =
        new RbelContentTreeNode(new RbelMultiMap<>(), "root".getBytes(StandardCharsets.UTF_8));

    RbelContentTreeNode newChild =
        new RbelContentTreeNode(null, "value".getBytes(StandardCharsets.UTF_8));
    root.addOrReplaceChild("newKey", newChild);

    assertThat(root.getChildNodesWithKey().getAll("newKey")).hasSize(1).contains(newChild);

    assertThat(newChild.getParentNode()).isEqualTo(root);
    assertThat(newChild.getKey()).hasValue("newKey");

    assertThat(root.getChildNodesWithKey().getAll("newKey").get(0).getRawStringContent())
        .as("Root content should be updated via updateContent()")
        .contains("value");
  }

  @Test
  void shouldThrowOnDuplicateKeyReplacement() {
    RbelContentTreeNode child1a =
        new RbelContentTreeNode(null, "v1".getBytes(StandardCharsets.UTF_8));
    RbelContentTreeNode child1b =
        new RbelContentTreeNode(null, "v2".getBytes(StandardCharsets.UTF_8));

    RbelMultiMap<RbelContentTreeNode> children = new RbelMultiMap<>();
    children.put("duplicateKey", child1a);
    children.put("duplicateKey", child1b);

    RbelContentTreeNode root =
        new RbelContentTreeNode(children, "root".getBytes(StandardCharsets.UTF_8));

    RbelContentTreeNode replacement =
        new RbelContentTreeNode(null, "replacement".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> root.addOrReplaceChild("duplicateKey", replacement))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("multiple entries with that key existed");
  }
}
