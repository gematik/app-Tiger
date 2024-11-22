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
 *
 */

package de.gematik.test.tiger.testenvmgr.api.model.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.platform.engine.TestDescriptor;

public class DummyTestContainerDescriptor extends DummyTestDescriptor {

  private final Collection<TestDescriptor> children = new ArrayList<>();

  public DummyTestContainerDescriptor(Collection<TestDescriptor> children) {
    super("dummyContainerDescriptor");
    children.forEach(this::addChild);
  }

  @Override
  public Type getType() {
    return Type.CONTAINER;
  }

  @Override
  public void addChild(TestDescriptor child) {
    child.setParent(this);
    children.add(child);
  }

  @Override
  public Set<? extends TestDescriptor> getChildren() {
    return new LinkedHashSet<>(children);
  }
}
