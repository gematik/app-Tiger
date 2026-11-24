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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.glue.TigerGluePackage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

class TigerGluePackageScannerTest {

  @Test
  void discoversPackageOfAnnotatedMarkerClass() {
    Set<String> packages = TigerGluePackageScanner.discoverGluePackages();
    assertThat(packages)
        .contains("de.gematik.test.tiger.lib.glue.testfixture", "com.example.tiger.glue");
    assertThat(packages.stream().toList()).isSorted();
  }

  @Test
  void indexMatchesLegacyScan() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(TigerGluePackage.class));
    var scanned =
        scanner.findCandidateComponents("de.gematik.test.tiger").stream()
            .map(BeanDefinition::getBeanClassName)
            .map(name -> name.substring(0, name.lastIndexOf('.')))
            .collect(Collectors.toSet());
    var indexed =
        TigerGluePackageScanner.discoverGluePackages().stream()
            .filter(name -> name.startsWith("de.gematik.test.tiger."))
            .collect(Collectors.toSet());
    assertThat(scanned).isNotEmpty();
    assertThat(indexed).containsExactlyInAnyOrderElementsOf(scanned);
  }

  @Test
  void discoversPackagesWithoutLoadingMarkersAndDeduplicatesResources() {
    ClassLoader loader =
        new ClassLoader(getClass().getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.example.tiger.glue.")) {
              throw new ClassNotFoundException("Marker classes must not be loaded");
            }
            return super.loadClass(name, resolve);
          }
        };
    assertThat(TigerGluePackageScanner.discoverGluePackages(List.of(loader, loader)))
        .containsExactlyElementsOf(TigerGluePackageScanner.discoverGluePackages());
  }

  @Test
  void usesOwnLoaderWithoutContextLoader() {
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(null);
      assertThat(TigerGluePackageScanner.discoverGluePackages()).contains("com.example.tiger.glue");
    } finally {
      thread.setContextClassLoader(original);
    }
  }

  @Test
  void corruptIndexIsSkipped(@TempDir Path temp) throws IOException {
    URL resource = Files.writeString(temp.resolve("broken.idx"), "not an index").toUri().toURL();
    ClassLoader loader =
        new ClassLoader(getClass().getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            return name.equals("META-INF/jandex.idx")
                ? Collections.enumeration(List.of(resource))
                : super.getResources(name);
          }
        };
    var loaders = List.of(loader);
    assertThat(TigerGluePackageScanner.discoverGluePackages(loaders)).isEmpty();
  }
}
