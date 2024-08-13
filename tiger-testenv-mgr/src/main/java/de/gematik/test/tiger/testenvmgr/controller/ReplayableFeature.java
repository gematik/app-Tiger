/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr.controller;

import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.Node;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;

/**
 * Wraps a given feature but delivers only one single pickle on the method getPickles(). In this way
 * we can execute a single test scenario (Pickle).
 */
@AllArgsConstructor
public class ReplayableFeature implements Feature {

  private final io.cucumber.core.gherkin.Feature feature;
  private final UUID pickleId;

  @Override
  public Pickle getPickleAt(Node node) {
    return feature.getPickleAt(node);
  }

  @Override
  public List<Pickle> getPickles() {
    return feature.getPickles().stream()
        .filter(p -> pickleId.toString().equals(p.getId()))
        .toList();
  }

  @Override
  public URI getUri() {
    return feature.getUri();
  }

  @Override
  public String getSource() {
    return feature.getSource();
  }

  @Override
  public Iterable<?> getParseEvents() {
    return feature.getParseEvents();
  }

  @Override
  public Collection<Node> elements() {
    return feature.elements();
  }

  @Override
  public Location getLocation() {
    return feature.getLocation();
  }

  @Override
  public Optional<String> getKeyword() {
    return feature.getKeyword();
  }

  @Override
  public Optional<String> getName() {
    return feature.getName();
  }

  @Override
  public Optional<Node> getParent() {
    return feature.getParent();
  }
}
