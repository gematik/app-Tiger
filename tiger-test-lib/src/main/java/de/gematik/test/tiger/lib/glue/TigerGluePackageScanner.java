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
package de.gematik.test.tiger.lib.glue;

import de.gematik.test.tiger.common.glue.TigerGluePackage;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Discovers Cucumber glue packages contributed by Tiger extensions via the {@link TigerGluePackage}
 * marker annotation, by scanning {@code de.gematik.test.tiger} on the classpath. Mirrors the
 * {@code @TigerServerType} scan in {@code TigerTestEnvMgr} (see {@code
 * lookupServerPluginsInClasspath}).
 *
 * <p>Result is the distinct set of packages of all {@link TigerGluePackage}-annotated classes.
 * Multiple annotated classes in the same package collapse to one entry; ordering is deterministic
 * (alphabetical) so the generated {@code --glue} args are reproducible.
 *
 * <p>Used by {@code TigerCucumberRunner} to widen the Cucumber glue list before delegating to the
 * engine, so extensions like {@code tiger-canopy-extension} don't force every downstream test suite
 * to add their glue package to {@code @ConfigurationParameter(cucumber.glue, …)}.
 */
@Slf4j
public final class TigerGluePackageScanner {

  /** Root package of the classpath scan — kept narrow for boot speed. */
  static final String SCAN_ROOT = "de.gematik.test.tiger";

  private TigerGluePackageScanner() {}

  /** Returns the distinct set of glue packages contributed via {@link TigerGluePackage}. */
  public static Set<String> discoverGluePackages() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(TigerGluePackage.class));
    Set<String> packages = new TreeSet<>();
    for (BeanDefinition bd : scanner.findCandidateComponents(SCAN_ROOT)) {
      String fqn = bd.getBeanClassName();
      if (fqn == null) {
        continue;
      }
      int lastDot = fqn.lastIndexOf('.');
      if (lastDot > 0) {
        packages.add(fqn.substring(0, lastDot));
      }
    }
    if (!packages.isEmpty()) {
      log.info("Auto-discovered Tiger glue packages: {}", packages);
    }
    return packages;
  }
}
