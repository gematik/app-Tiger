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
package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Enables the usage of the RbelPathExecutor. The methods are called by the RbelPathExecutor, but
 * not only. The description however focuses on that use-case
 */
public abstract class RbelPathAble {

  public abstract Optional<? extends RbelPathAble> getFirst(String key);

  public abstract RbelPathAble getParentNode();

  public abstract List<? extends RbelPathAble> getAll(String subkey);

  public abstract List<? extends RbelPathAble> getChildNodes();

  public abstract RbelMultiMap<? extends RbelPathAble> getChildNodesWithKey();

  public abstract Optional<String> getKey();

  public abstract String getRawStringContent();

  public abstract List<? extends RbelPathAble> findRbelPathMembers(String rbelPath);

  /**
   * Should return the list of search-relevant nodes. Normally this would be the identity (the
   * default implementation given here), but for virtual nodes (content-nodes in a rbel-tree for
   * example) that should not be part of the actual search-tree the child-nodes should be returned.
   */
  public List<RbelPathAble> descendToContentNodeIfAdvised() {
    return List.of(this);
  }

  /** Should this element be present in the final RbelPath results? */
  public boolean shouldElementBeKeptInFinalResult() {
    return true;
  }

  public String findNodePath() {
    LinkedList<Optional<String>> keyList = new LinkedList<>();
    final AtomicReference<RbelPathAble> ptr = new AtomicReference<>(this);
    while (ptr.get().getParentNode() != null) {
      keyList.addFirst(
          ptr.get().getParentNode().getChildNodesWithKey().stream()
              .filter(entry -> entry.getValue().equals(ptr.get()))
              .map(Map.Entry::getKey)
              .findFirst());
      ptr.set(ptr.get().getParentNode());
    }
    return keyList.stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.joining("."));
  }
}
