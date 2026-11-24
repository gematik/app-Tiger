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
import de.gematik.test.tiger.common.util.JandexIndexReader;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.jboss.jandex.DotName;

/**
 * Discovers Cucumber glue packages contributed by Tiger extensions via the {@link TigerGluePackage}
 * marker annotation in {@code META-INF/jandex.idx} resources, without loading the marker classes.
 * Annotated classes may reside in any package.
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

  private static final DotName GLUE_PACKAGE_ANNOTATION =
      DotName.createSimple(TigerGluePackage.class.getName());

  private TigerGluePackageScanner() {}

  /** Returns the distinct set of glue packages contributed via {@link TigerGluePackage}. */
  public static Set<String> discoverGluePackages() {
    return discoverGluePackages(JandexIndexReader.classLoaders(TigerGluePackageScanner.class));
  }

  static Set<String> discoverGluePackages(List<ClassLoader> loaders) {
    Set<String> packages = new TreeSet<>();
    for (var match :
        JandexIndexReader.findClassAnnotations(
            loaders,
            GLUE_PACKAGE_ANNOTATION,
            type ->
                !type.isAbstract()
                    && !type.isInterface()
                    && (type.enclosingClass() == null || Modifier.isStatic(type.flags())),
            TigerTestEnvException::new)) {
      String name = match.annotation().target().asClass().name().toString();
      int lastDot = name.lastIndexOf('.');
      if (lastDot > 0) {
        packages.add(name.substring(0, lastDot));
      }
    }
    if (!packages.isEmpty()) {
      log.info("Auto-discovered Tiger glue packages: {}", packages);
    }
    return packages;
  }
}
