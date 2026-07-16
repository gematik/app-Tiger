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

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

@Slf4j
public final class ConfigurationRedactor {
  private static final String DEFAULT_MARKER = "[REDACTED]";

  private ConfigurationRedactor() {}

  /**
   * Redact the configured paths (dot-separated) in the given ObjectNode. Paths are relative to the
   * root object and support '*' as wildcard for arrays/fields.
   *
   * <p>Examples: "proxyRoutes.*.basicAuth" "fileSaveInfo.password"
   */
  public static void applyRedactions(ObjectNode root, List<String> paths) {
    if (root == null || paths == null || paths.isEmpty()) return;
    for (String path : paths) {
      if (path == null || path.isBlank()) continue;
      try {
        redactPath(root, path.trim());
      } catch (Exception e) {
        log.warn("Failed to apply redaction for path '{}': {}", path, e.getMessage());
      }
    }
  }

  private static void redactPath(JsonNode current, String path) {
    String[] segments = path.split("\\.");
    redactRecursive(current, segments, 0);
  }

  private static void redactRecursive(JsonNode current, String[] segments, int idx) {
    if (current == null) return;
    if (idx >= segments.length) return;

    if (current.isObject()) {
      redactObjectNode((ObjectNode) current, segments, idx);
    } else if (current.isArray()) {
      redactArrayNode((ArrayNode) current, segments, idx);
    } else {
      // primitive reached before leaf: nothing to do
    }
  }

  private static void redactObjectNode(ObjectNode obj, String[] segments, int idx) {
    String seg = segments[idx];
    boolean isLeaf = idx == segments.length - 1;

    if ("*".equals(seg)) {
      // iterate over current field names snapshot to avoid concurrent modification
      List<String> fields = new ArrayList<>(obj.propertyNames());
      for (String fieldName : fields) {
        if (isLeaf) {
          obj.put(fieldName, DEFAULT_MARKER);
        } else {
          JsonNode child = obj.get(fieldName);
          redactRecursive(child, segments, idx + 1);
        }
      }
    } else {
      JsonNode child = obj.get(seg);
      if (child == null) {
        return;
      }
      if (isLeaf) {
        obj.put(seg, DEFAULT_MARKER);
      } else {
        redactRecursive(child, segments, idx + 1);
      }
    }
  }

  // Handle array nodes for the current segment index
  private static void redactArrayNode(ArrayNode arr, String[] segments, int idx) {
    String seg = segments[idx];
    boolean isLeaf = idx == segments.length - 1;

    if ("*".equals(seg)) {
      for (int i = 0; i < arr.size(); i++) {
        if (isLeaf) {
          arr.set(i, StringNode.valueOf(DEFAULT_MARKER));
        } else {
          redactRecursive(arr.get(i), segments, idx + 1);
        }
      }
    } else {
      // try numeric index
      try {
        int index = Integer.parseInt(seg);
        if (index >= 0 && index < arr.size()) {
          if (isLeaf) {
            arr.set(index, StringNode.valueOf(DEFAULT_MARKER));
          } else {
            redactRecursive(arr.get(index), segments, idx + 1);
          }
        }
      } catch (NumberFormatException ignored) {
        // not an index, nothing to do
      }
    }
  }
}
