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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import de.gematik.test.tiger.testenvmgr.api.model.TestDescriptionDto;
import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.launcher.TestIdentifier;

/* The org.modelmapper.ModelMapper helps us mapping
data from DTOs to internal data classes.
The mapping is partially automagically. The tests here ensure that the mapping works as expected.
 */
class TestDescriptionMapperTest {
  private static final TestDescriptionMapper testDescriptionMapper =
      new TestDescriptionMapperImpl();

  @Test
  void testMappingTestIdentifier_to_TestDescription() {

    TestIdentifier identifier = TestIdentifier.from(new DummyTestDescriptor());

    TestDescriptionDto testDescription =
        testDescriptionMapper.testIdentifierToTestDescription(identifier);
    assertThat(testDescription.getDisplayName()).isEqualTo(identifier.getDisplayName());
    assertThat(testDescription.getSourceFile())
        .isEqualTo(
            identifier
                .getSource()
                .map(FileSource.class::cast)
                .map(f -> f.getUri().normalize().toString())
                .orElseThrow());
    assertThat(testDescription.getTags())
        .isEqualTo(identifier.getTags().stream().map(TestTag::getName).collect(Collectors.toSet()));
  }

  @Test
  void mappingTestIdentifier_withFileSource() {
    TestIdentifier identifier = TestIdentifier.from(new DummyTestDescriptor());

    TestDescriptionDto testDescription =
        testDescriptionMapper.testIdentifierToTestDescription(identifier);
    assertThat(testDescription.getSourceFile())
        .isEqualTo(
            identifier
                .getSource()
                .map(FileSource.class::cast)
                .map(f -> f.getUri().normalize().toString())
                .orElseThrow());
  }

  @Test
  void mappingTestIdentifier_withClasspathResourceSource() {
    TestIdentifier identifier = TestIdentifier.from(new DummyTestDescriptorWithClasspathSource());

    TestDescriptionDto testDescription =
        testDescriptionMapper.testIdentifierToTestDescription(identifier);
    assertThat(testDescription.getSourceFile()).isEqualTo("classpath:a/classpath/resource");
  }

  @Test
  void mappingTestIdentifier_withUnsupportedSource() {
    TestIdentifier identifier = TestIdentifier.from(new DummyTestDescriptorWithUnsupportedSource());

    assertThatThrownBy(() -> testDescriptionMapper.testIdentifierToTestDescription(identifier))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported test source type");
  }

  static class DummyTestDescriptorWithClasspathSource extends DummyTestDescriptor {
    @Override
    public Optional<TestSource> getSource() {
      return Optional.of(ClasspathResourceSource.from("a/classpath/resource"));
    }
  }

  static class DummyTestDescriptorWithUnsupportedSource extends DummyTestDescriptor {
    @Override
    public Optional<TestSource> getSource() {
      return Optional.of(new TestSource() {});
    }
  }

  static class DummyTestDescriptor implements TestDescriptor {

    @Override
    public UniqueId getUniqueId() {
      return UniqueId.forEngine("cucumber").append("test", "dummyTestDescriptor");
    }

    @Override
    public String getDisplayName() {
      return "dummy display name";
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
      return Optional.empty();
    }

    @Override
    public void setParent(TestDescriptor parent) {}

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
}
