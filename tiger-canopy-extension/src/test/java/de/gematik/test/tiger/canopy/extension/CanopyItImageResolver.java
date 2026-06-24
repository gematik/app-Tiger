/*
 *
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.test.tiger.canopy.extension;

import com.github.dockerjava.api.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.DockerClientFactory;

/**
 * Resolves the canopy image to use for integration tests:
 *
 * <ol>
 *   <li>If the system property {@code tiger.canopy.it.image} is set, that value wins (developer
 *       override / CI pipeline injection).
 *   <li>Otherwise the local Docker daemon is probed for the first known canopy image tag in
 *       priority order (latest-built dev tag first). If a match is present locally, that tag is
 *       returned and the IT runs.
 *   <li>Otherwise {@link Optional#empty()} — caller should {@code assumeTrue(...).isPresent()} and
 *       skip the IT.
 * </ol>
 *
 * <p>Auto-detection deliberately does <em>not</em> trigger a pull. CI pipelines that want to run
 * the IT against a specific tag should set {@code -Dtiger.canopy.it.image=...} explicitly.
 */
final class CanopyItImageResolver {

  static final String PROPERTY = "tiger.canopy.it.image";

  /**
   * Known canopy image tags, in priority order. Add new candidates here; do NOT add transient SHA
   * tags (those would make IT runs depend on a specific dev's local docker cache).
   *
   * <p>Order rationale:
   *
   * <ol>
   *   <li>Public DockerHub tags first — what end users actually run.
   *   <li>Internal CI tag {@code tiger/tiger-canopy:latest} — produced by the Tiger Jenkins
   *       pipeline's "Build Docker Images" stage, so post-build ITs on CI pick up the image that
   *       was just built from this very commit.
   *   <li>Local dev tags last — convenience for developers running {@code docker build -t
   *       tiger-canopy:test ...} on their workstation.
   * </ol>
   */
  static final List<String> CANDIDATE_TAGS =
      List.of(
          "gematik1/tiger-canopy-image:latest",
          "gematik1/tiger-canopy-image:dev",
          "tiger/tiger-canopy:latest",
          "tiger-canopy:test",
          "tiger-canopy:latest");

  private CanopyItImageResolver() {}

  static Optional<String> resolve() {
    String override = System.getProperty(PROPERTY);
    if (StringUtils.isNotBlank(override)) {
      return Optional.of(override.trim());
    }
    if (!DockerClientFactory.instance().isDockerAvailable()) {
      return Optional.empty();
    }
    var client = DockerClientFactory.instance().client();
    for (String tag : CANDIDATE_TAGS) {
      try {
        client.inspectImageCmd(tag).exec();
        return Optional.of(tag);
      } catch (NotFoundException ignored) {
        // try next candidate
      } catch (RuntimeException unexpected) {
        // Daemon issue / permission / etc — don't mask, but don't crash the IT either: just
        // skip auto-detect and let the caller fail the assumeTrue.
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
