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
package de.gematik.test.tiger.testenvmgr.service;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.UniqueId;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PathsConverter { // NOSONAR - no autowired needed for default constructor.
  public static final String FEATURE_SEGMENT_TYPE = "feature";
  public static final String FILE_SCHEME_PREFIX = "file:/";
  public static final String RELATIVE_SCHEME_PREFIX =
      "relativeFile:/"; // this is not a standard prefix. We use it for better control of the
  // conversion
  public final Path referencePath = Path.of("").toAbsolutePath().normalize();

  private UniqueId convertPath(
      UniqueId identifier, Path referencePath, BiFunction<Path, Path, String> pathConverter) {
    var convertedPath =
        identifier.getSegments().stream()
            .filter(e -> FEATURE_SEGMENT_TYPE.equals(e.getType()))
            .map(UniqueId.Segment::getValue)
            .filter(v -> v.startsWith(RELATIVE_SCHEME_PREFIX) || v.startsWith(FILE_SCHEME_PREFIX))
            .findAny()
            .map(this::convertToPath)
            .map(p -> pathConverter.apply(p, referencePath));

    return convertedPath.map(s -> uniqueIdWithNewFeatureSegment(identifier, s)).orElse(identifier);
  }

  private Path convertToPath(String featureSegmentValue) {
    if (featureSegmentValue.startsWith(RELATIVE_SCHEME_PREFIX)) {
      return Path.of(featureSegmentValue.substring(RELATIVE_SCHEME_PREFIX.length()));
    } else {
      return Path.of(URI.create(featureSegmentValue));
    }
  }

  public List<UniqueId> relativizePaths(List<UniqueId> uniqueIds) {
    return uniqueIds.stream().map(uid -> relativizeFeaturePath(uid, referencePath)).toList();
  }

  public List<UniqueId> resolvePaths(List<UniqueId> uniqueIds) {
    return uniqueIds.stream().map(uid -> resolveFeaturePath(uid, referencePath)).toList();
  }

  protected UniqueId relativizeFeaturePath(UniqueId scenarioIdentifier, Path referencePath) {
    return convertPath(scenarioIdentifier, referencePath, this::relativizePath);
  }

  protected UniqueId resolveFeaturePath(UniqueId scenarioIdentifier, Path referencePath) {
    return convertPath(scenarioIdentifier, referencePath, this::resolvePath);
  }

  protected String normalizeSlashes(String path) {
    // Cucumber uses URIs which always have forward slashes.
    // We cant directly generate the URI for a relative path, or we end up always
    // with the absolute path. Therefore we replace the file separator
    if (File.separatorChar != '/') {
      return path.replace(File.separatorChar, '/');
    } else {
      return path;
    }
  }

  private UniqueId uniqueIdWithNewFeatureSegment(UniqueId uniqueId, String newFeatureSegmentValue) {
    // We cant create Segments directly (protected method)
    // and cant modify the list of segments inside a uniqueID.
    // Therefore we remove segments until we find the feature, add the feature segment with new
    // value
    // and readd all removed segments.
    Deque<UniqueId.Segment> removedSegments = new ArrayDeque<>();
    var toModify = uniqueId;
    while (toModify.getSegments().size() > 1) {
      var lastSegment = toModify.getLastSegment();
      toModify = toModify.removeLastSegment();
      if (FEATURE_SEGMENT_TYPE.equals(lastSegment.getType())) {
        // Need again
        toModify = toModify.append(FEATURE_SEGMENT_TYPE, newFeatureSegmentValue);
        break;
      } else {
        removedSegments.push(lastSegment);
      }
    }
    while (!removedSegments.isEmpty()) {
      toModify = toModify.append(removedSegments.pop());
    }
    return toModify;
  }

  protected String relativizePath(Path pathToRelativize, Path referencePath) {

    Path absoluteReference = referencePath.toAbsolutePath().normalize();
    Path absoluteTarget = pathToRelativize.toAbsolutePath().normalize();

    // RELATIVE_SCHEME_PREFIX is not a standard prefix, but we only use it as a
    // saving format that shall always be reconverted to absolute path when rereading the path.
    return RELATIVE_SCHEME_PREFIX
        + normalizeSlashes(absoluteReference.relativize(absoluteTarget).toString());
  }

  protected String resolvePath(Path pathToResolve, Path referencePath) {
    var result = referencePath.toAbsolutePath().resolve(pathToResolve).normalize().toAbsolutePath();

    // Using the URI as created from File to ensure compatibility with the Serenity created URIs
    return new File(result.toString()).getAbsoluteFile().toURI().toString();
  }
}
