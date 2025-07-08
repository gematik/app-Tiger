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
package de.gematik.test.tiger.testenvmgr.env;

import java.net.URI;
import java.util.LinkedHashMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.UriSource;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeatureUpdate {

  private LinkedHashMap<String, ScenarioUpdate> scenarios;
  private String description;
  private TestResult status;
  private String sourcePath;

  public static class FeatureUpdateBuilder {
    public FeatureUpdateBuilder sourcePathFromUri(URI sourcePathUri) {
      this.sourcePath = sourcePathUri.getPath();
      return this;
    }

    public FeatureUpdateBuilder sourcePathForSource(TestSource source) {
      if (source instanceof ClasspathResourceSource classpathSource) {
        this.sourcePath = classpathSource.getClasspathResourceName();
      } else if (source instanceof UriSource uriSource) {
        this.sourcePath = uriSource.getUri().getPath();
      }
      return this;
    }
  }
}
