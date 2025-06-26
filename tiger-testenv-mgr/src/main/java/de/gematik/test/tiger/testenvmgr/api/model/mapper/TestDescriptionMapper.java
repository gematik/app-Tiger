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

import de.gematik.test.tiger.testenvmgr.api.model.TestDescriptionDto;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.launcher.TestIdentifier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * maps {@link TigerTestIdentifier} into {@link
 * de.gematik.test.tiger.testenvmgr.api.model.TestDescriptionDto} *
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TestDescriptionMapper {

  @Mapping(source = "source", target = "sourceFile")
  TestDescriptionDto testIdentifierToTestDescription(TestIdentifier testIdentifier);

  @Mapping(source = "testIdentifier.uniqueId", target = "uniqueId")
  @Mapping(source = "testIdentifier.source", target = "sourceFile")
  @Mapping(source = "displayName", target = "displayName")
  @Mapping(source = "testIdentifier.tags", target = "tags")
  TestDescriptionDto tigerTestIdentifierToTestDescription(TigerTestIdentifier tigerTestIdentifier);

  default Set<String> testTagsToStrings(Set<TestTag> tags) {
    return tags.stream().map(TestTag::getName).collect(Collectors.toSet());
  }

  default String sourceToSourceFileString(Optional<TestSource> source) {
    return source
        .map(
            s -> {
              if (s instanceof FileSource fileSource) {
                return fileSource.getUri().normalize().toString();
              } else if (s instanceof ClasspathResourceSource classpathResourceSource) {
                return "classpath:" + classpathResourceSource.getClasspathResourceName();
              } else {
                throw new IllegalArgumentException("unsupported test source type: " + s.getClass());
              }
            })
        .orElse(null);
  }
}
