/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.rbellogger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import com.example.tiger.CustomerConverter;
import de.gematik.rbellogger.RbelConverterIndex.ConverterDescriptor;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.exceptions.RbelInitializationException;
import de.gematik.rbellogger.facets.vau.AbstractAslDecryptionConverter;
import de.gematik.rbellogger.facets.vau.asl.RbelAslConverter;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaConverter;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaKeyDeriver;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class RbelConverterIndexTest {

  @Test
  void initializerShouldUseIndexedConverters() {
    assertThat(RbelConverterInitializer.findAllAvailableConverters())
        .extracting(ConverterDescriptor::className)
        .containsExactlyInAnyOrderElementsOf(
            RbelConverterIndex.readIndex().stream().map(ConverterDescriptor::className).toList());
  }

  @Test
  void indexedMetadataShouldMatchConverterInfoAnnotations() {
    Map<String, ConverterDescriptor> indexed =
        RbelConverterIndex.readIndex().stream()
            .collect(Collectors.toMap(ConverterDescriptor::className, Function.identity()));

    assertThat(indexed).isNotEmpty();
    indexed
        .values()
        .forEach(
            descriptor -> {
              var info = descriptor.loadClass().getAnnotation(ConverterInfo.class);
              assertThat(descriptor.addAutomatically())
                  .as(descriptor.className())
                  .isEqualTo(info == null || info.addAutomatically());
              assertThat(descriptor.onlyActivateFor())
                  .containsExactlyElementsOf(
                      info == null ? List.of() : List.of(info.onlyActivateFor()));
              assertThat(descriptor.dependsOn())
                  .containsExactlyElementsOf(
                      info == null
                          ? List.of()
                          : Arrays.stream(info.dependsOn()).map(Class::getName).toList());
            });
  }

  @Test
  void indexShouldContainConverterInfoAttributes() {
    var vauConverter =
        RbelConverterIndex.readIndex().stream()
            .filter(d -> d.className().equals(RbelVauEpaConverter.class.getName()))
            .findFirst()
            .orElseThrow();

    assertThat(vauConverter.onlyActivateFor()).containsExactly("epa-vau");
    assertThat(vauConverter.dependsOn()).containsExactly(RbelVauEpaKeyDeriver.class.getName());
    assertThat(vauConverter.addAutomatically()).isTrue();
  }

  @Test
  void discoversCustomerConverterOutsideGematikPackage() {
    var descriptor =
        RbelConverterIndex.readIndex().stream()
            .filter(d -> d.className().equals(CustomerConverter.class.getName()))
            .findFirst()
            .orElseThrow();
    assertThat(descriptor.loadClass()).isEqualTo(CustomerConverter.class);
    assertThat(descriptor.addAutomatically()).isFalse();
    assertThat(descriptor.onlyActivateFor()).containsExactly("customer");
  }

  @Test
  void mergesIndexesForInheritanceAcrossJars(@TempDir Path temp) throws IOException {
    URL base =
        writeIndex(
            temp.resolve("base.idx"),
            RbelConverterPlugin.class,
            ConverterInfo.class,
            AbstractAslDecryptionConverter.class);
    URL extension = writeIndex(temp.resolve("extension.idx"), RbelAslConverter.class);
    var descriptors = RbelConverterIndex.readIndex(List.of(loader(base, extension)));
    assertThat(descriptors)
        .extracting(ConverterDescriptor::className)
        .containsExactly(RbelAslConverter.class.getName());
    assertThat(descriptors.getFirst().onlyActivateFor()).containsExactly("asl");
    assertThat(descriptors.getFirst().loadClass()).isEqualTo(RbelAslConverter.class);
  }

  @Test
  void corruptIndexIsSkipped(@TempDir Path temp) throws IOException {
    Path corrupt = Files.writeString(temp.resolve("corrupt.idx"), "not an index");
    var loaders = List.of(loader(corrupt.toUri().toURL()));
    assertThat(RbelConverterIndex.readIndex(loaders)).isEmpty();
  }

  @Test
  void unindexedConvertersAreNotDiscovered() {
    var indexed = RbelConverterIndex.readIndex();
    try (var discovery = mockStatic(RbelConverterIndex.class)) {
      discovery
          .when(RbelConverterIndex::readIndex)
          .thenReturn(
              indexed.stream()
                  .filter(d -> !d.className().equals(RbelAslConverter.class.getName()))
                  .toList());
      assertThat(RbelConverterInitializer.findAllAvailableConverters())
          .extracting(ConverterDescriptor::className)
          .doesNotContain(RbelAslConverter.class.getName());
    }
  }

  @Test
  void missingSeedFailsExplicitly() {
    var indexed =
        RbelConverterIndex.readIndex().stream()
            .filter(d -> !d.className().equals(RbelVauEpaKeyDeriver.class.getName()))
            .toList();
    try (var discovery = mockStatic(RbelConverterIndex.class)) {
      discovery.when(RbelConverterIndex::readIndex).thenReturn(indexed);
      assertThatThrownBy(RbelConverterInitializer::findAllAvailableConverters)
          .isInstanceOf(RbelInitializationException.class)
          .hasMessageContaining(RbelVauEpaKeyDeriver.class.getName())
          .hasMessageContaining("jandex-maven-plugin");
    }
  }

  private URL writeIndex(Path file, Class<?>... classes) throws IOException {
    try (var output = Files.newOutputStream(file)) {
      new IndexWriter(output).write(Index.of(classes));
    }
    return file.toUri().toURL();
  }

  private ClassLoader loader(URL... urls) {
    return new ClassLoader(getClass().getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        return name.equals(RbelConverterIndex.INDEX_RESOURCE)
            ? Collections.enumeration(List.of(urls))
            : super.getResources(name);
      }
    };
  }
}
