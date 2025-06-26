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
package de.gematik.test.tiger.testenvmgr.api.model.mapper;

import java.io.File;
import java.util.Optional;
import java.util.Set;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.FileSource;

public class DummyTestDescriptor implements TestDescriptor {

  private final String descriptorName;
  private TestDescriptor parent = null;

  public DummyTestDescriptor(String descriptorName) {
    this.descriptorName = descriptorName;
  }

  public DummyTestDescriptor() {
    this("dummyTestDescriptor");
  }

  @Override
  public UniqueId getUniqueId() {
    return UniqueId.forEngine("cucumber").append("test", descriptorName);
  }

  @Override
  public String getDisplayName() {
    return descriptorName + ": dummy display name";
  }

  @Override
  public Set<TestTag> getTags() {
    return Set.of(TestTag.create("ThisATag"), TestTag.create("ThisAnotherTag"));
  }

  @Override
  public Optional<TestSource> getSource() {
    return Optional.of(FileSource.from(new File("/a/file/source")));
  }

  @Override
  public Optional<TestDescriptor> getParent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public void setParent(TestDescriptor parent) {
    this.parent = parent;
  }

  @Override
  public Set<? extends TestDescriptor> getChildren() {
    return Set.of();
  }

  @Override
  public void addChild(TestDescriptor descriptor) {}

  @Override
  public void removeChild(TestDescriptor descriptor) {}

  @Override
  public void removeFromHierarchy() {}

  @Override
  public Type getType() {
    return Type.TEST;
  }

  @Override
  public Optional<? extends TestDescriptor> findByUniqueId(UniqueId uniqueId) {
    return Optional.empty();
  }
}
