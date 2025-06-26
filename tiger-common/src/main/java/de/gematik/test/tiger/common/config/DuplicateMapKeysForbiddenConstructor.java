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
package de.gematik.test.tiger.common.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.parser.ParserException;

/** A specialized {@link Constructor} that checks for duplicate keys. */
public class DuplicateMapKeysForbiddenConstructor extends SafeConstructor {

  public DuplicateMapKeysForbiddenConstructor() {
    super(new LoaderOptions());
  }

  @Override
  protected Map<Object, Object> constructMapping(MappingNode node) {
    try {
      List<String> keys =
          node.getValue().stream().map(v -> ((ScalarNode) v.getKeyNode()).getValue()).toList();
      Set<String> duplicates = findDuplicates(keys);
      if (!duplicates.isEmpty()) {
        throw new TigerConfigurationException(
            "Duplicate keys in yaml file ('" + String.join(",", duplicates) + "')!");
      }
    } catch (TigerConfigurationException tcex) {
      throw tcex;
    } catch (Exception e) {
      throw new TigerConfigurationException("Duplicate keys in yaml file!", e);
    }
    try {
      return super.constructMapping(node);
    } catch (IllegalStateException e) {
      throw new ParserException(
          "while parsing MappingNode", node.getStartMark(), e.getMessage(), node.getEndMark());
    }
  }

  private <T> Set<T> findDuplicates(Collection<T> collection) {
    Set<T> uniques = new HashSet<>();
    return collection.stream().filter(e -> !uniques.add(e)).collect(Collectors.toSet());
  }
}
