/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.proxy.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ConfigurationRedactorTest {

  private static final String REDACTED = "[REDACTED]";

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void redactsSingleTopLevelField() {
    ObjectNode root = object("topSecret", "cleartext-value");

    ConfigurationRedactor.applyRedactions(root, List.of("topSecret"));

    assertThat(root.path("topSecret").asString()).isEqualTo(REDACTED);
  }

  @Test
  void redactsSingleNestedField() {
    ObjectNode child = object("sensitiveField", "cleartext-value");
    ObjectNode root = mapper.createObjectNode();
    root.set("parent", child);

    ConfigurationRedactor.applyRedactions(root, List.of("parent.sensitiveField"));

    assertThat(root.path("parent").path("sensitiveField").asString()).isEqualTo(REDACTED);
  }

  @Test
  void leavesAdjacentFieldsIntactOnSinglePath() {
    ObjectNode child = object("target", "secret", "sibling", "keep-me");
    ObjectNode root = mapper.createObjectNode();
    root.set("group", child);

    ConfigurationRedactor.applyRedactions(root, List.of("group.target"));

    assertThat(root.path("group").path("target").asString()).isEqualTo(REDACTED);
    assertThat(root.path("group").path("sibling").asString()).isEqualTo("keep-me");
  }

  @Test
  void wildcardRedactsAllFieldsInObject() {
    ObjectNode child = object("fieldA", "val-a", "fieldB", "val-b");
    ObjectNode root = mapper.createObjectNode();
    root.set("settings", child);

    ConfigurationRedactor.applyRedactions(root, List.of("settings.*"));

    assertThat(root.path("settings").path("fieldA").asString()).isEqualTo(REDACTED);
    assertThat(root.path("settings").path("fieldB").asString()).isEqualTo(REDACTED);
  }

  @Test
  void wildcardInMidPathTraversesObjectThenRedactsLeaf() {
    ObjectNode child1 = object("token", "abc");
    ObjectNode child2 = object("token", "xyz");
    ObjectNode parent = mapper.createObjectNode();
    parent.set("svcA", child1);
    parent.set("svcB", child2);
    ObjectNode root = mapper.createObjectNode();
    root.set("services", parent);

    ConfigurationRedactor.applyRedactions(root, List.of("services.*.token"));

    assertThat(root.path("services").path("svcA").path("token").asString()).isEqualTo(REDACTED);
    assertThat(root.path("services").path("svcB").path("token").asString()).isEqualTo(REDACTED);
  }

  @Test
  void wildcardReplacesEveryStringElementInArray() {
    ObjectNode root = mapper.createObjectNode();
    ArrayNode arr = mapper.createArrayNode();
    arr.add("value-a");
    arr.add("value-b");
    arr.add("value-c");
    root.set("tokens", arr);

    ConfigurationRedactor.applyRedactions(root, List.of("tokens.*"));

    assertThat(root.path("tokens").path(0).asString()).isEqualTo(REDACTED);
    assertThat(root.path("tokens").path(1).asString()).isEqualTo(REDACTED);
    assertThat(root.path("tokens").path(2).asString()).isEqualTo(REDACTED);
  }

  @Test
  void numericIndexReplacesStringElementInArray() {
    ObjectNode root = mapper.createObjectNode();
    ArrayNode arr = mapper.createArrayNode();
    arr.add("keep");
    arr.add("replace-me");
    arr.add("keep");
    root.set("tokens", arr);

    ConfigurationRedactor.applyRedactions(root, List.of("tokens.1"));

    assertThat(root.path("tokens").path(0).asString()).isEqualTo("keep");
    assertThat(root.path("tokens").path(1).asString()).isEqualTo(REDACTED);
    assertThat(root.path("tokens").path(2).asString()).isEqualTo("keep");
  }

  @Test
  void wildcardRedactsMatchingFieldInEveryArrayElement() {
    ObjectNode root = mapper.createObjectNode();
    root.set(
        "items",
        arrayOf(object("label", "a", "secret", "s1"), object("label", "b", "secret", "s2")));

    ConfigurationRedactor.applyRedactions(root, List.of("items.*.secret"));

    assertThat(root.path("items").path(0).path("secret").asString()).isEqualTo(REDACTED);
    assertThat(root.path("items").path(1).path("secret").asString()).isEqualTo(REDACTED);
  }

  @Test
  void wildcardLeavesOtherArrayElementFieldsIntact() {
    ObjectNode root = mapper.createObjectNode();
    root.set(
        "items",
        arrayOf(object("label", "a", "secret", "s1"), object("label", "b", "secret", "s2")));

    ConfigurationRedactor.applyRedactions(root, List.of("items.*.secret"));

    assertThat(root.path("items").path(0).path("label").asString()).isEqualTo("a");
    assertThat(root.path("items").path(1).path("label").asString()).isEqualTo("b");
  }

  @Test
  void numericIndexRedactsOnlyThatArrayElement() {
    ObjectNode root = mapper.createObjectNode();
    root.set(
        "items", arrayOf(object("secret", "s0"), object("secret", "s1"), object("secret", "s2")));

    ConfigurationRedactor.applyRedactions(root, List.of("items.1.secret"));

    assertThat(root.path("items").path(0).path("secret").asString()).isEqualTo("s0");
    assertThat(root.path("items").path(1).path("secret").asString()).isEqualTo(REDACTED);
    assertThat(root.path("items").path(2).path("secret").asString()).isEqualTo("s2");
  }

  @Test
  void nonExistingTopLevelPathLeavesRootUnchanged() {
    ObjectNode root = object("existing", "value");

    ConfigurationRedactor.applyRedactions(root, List.of("nonExistent"));

    assertThat(root.path("existing").asString()).isEqualTo("value");
  }

  @Test
  void nonExistingNestedPathLeavesRootUnchanged() {
    ObjectNode child = object("field", "value");
    ObjectNode root = mapper.createObjectNode();
    root.set("parent", child);

    ConfigurationRedactor.applyRedactions(root, List.of("parent.noSuchField"));

    assertThat(root.path("parent").path("field").asString()).isEqualTo("value");
  }

  @Test
  void nonExistingIntermediateSegmentLeavesRootUnchanged() {
    ObjectNode root = object("field", "value");

    ConfigurationRedactor.applyRedactions(root, List.of("ghost.child.field"));

    assertThat(root.path("field").asString()).isEqualTo("value");
  }

  @Test
  void outOfBoundsArrayIndexLeavesRootUnchanged() {
    ObjectNode root = mapper.createObjectNode();
    root.set("items", arrayOf(object("secret", "s0")));

    ConfigurationRedactor.applyRedactions(root, List.of("items.99.secret"));

    assertThat(root.path("items").path(0).path("secret").asString()).isEqualTo("s0");
  }

  @Test
  void doesNotThrowWhenRootIsNull() {
    assertThatCode(() -> ConfigurationRedactor.applyRedactions(null, List.of("a.b")))
        .doesNotThrowAnyException();
  }

  @Test
  void doesNotThrowWhenPathListIsNull() {
    ObjectNode root = object("field", "value");
    assertThatCode(() -> ConfigurationRedactor.applyRedactions(root, null))
        .doesNotThrowAnyException();
  }

  @Test
  void ignoresNullBlankAndWhitespaceOnlyPathEntries() {
    ObjectNode root = object("field", "value");

    ConfigurationRedactor.applyRedactions(root, Arrays.asList(null, "", "   "));

    assertThat(root.path("field").asString()).isEqualTo("value");
  }

  @Test
  void appliesMultiplePathsInOneCall() {
    ObjectNode root = object("alpha", "a-secret", "beta", "b-secret", "gamma", "keep");

    ConfigurationRedactor.applyRedactions(root, List.of("alpha", "beta"));

    assertThat(root.path("alpha").asString()).isEqualTo(REDACTED);
    assertThat(root.path("beta").asString()).isEqualTo(REDACTED);
    assertThat(root.path("gamma").asString()).isEqualTo("keep");
  }

  /** Creates an ObjectNode from alternating key/value string pairs. */
  private ObjectNode object(String... keyValues) {
    ObjectNode node = mapper.createObjectNode();
    for (int i = 0; i < keyValues.length - 1; i += 2) {
      node.put(keyValues[i], keyValues[i + 1]);
    }
    return node;
  }

  private ArrayNode arrayOf(ObjectNode... elements) {
    ArrayNode arr = mapper.createArrayNode();
    for (ObjectNode el : elements) {
      arr.add(el);
    }
    return arr;
  }
}
