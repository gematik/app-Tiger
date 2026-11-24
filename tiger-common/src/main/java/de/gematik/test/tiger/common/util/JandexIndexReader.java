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
package de.gematik.test.tiger.common.util;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.UnsupportedVersion;

@Slf4j
public final class JandexIndexReader {

  public static final String INDEX_RESOURCE = "META-INF/jandex.idx";

  private JandexIndexReader() {}

  public static List<ClassLoader> classLoaders(Class<?> owner) {
    var loaders = new ArrayList<ClassLoader>();
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    if (contextLoader != null) {
      loaders.add(contextLoader);
    }
    ClassLoader ownLoader = owner.getClassLoader();
    if (ownLoader != null && !loaders.contains(ownLoader)) {
      loaders.add(ownLoader);
    }
    return List.copyOf(loaders);
  }

  /** Returns matches in resource order, then class-name order within each resource. */
  public static List<IndexedAnnotation> findClassAnnotations(
      List<ClassLoader> loaders,
      DotName annotationName,
      Predicate<ClassInfo> filter,
      BiFunction<String, Exception, ? extends RuntimeException> exceptionFactory) {
    var matches = new ArrayList<IndexedAnnotation>();
    for (var resource : readIndexes(loaders, exceptionFactory)) {
      try {
        resource.index().getAnnotations(annotationName).stream()
            .filter(annotation -> annotation.target().kind() == AnnotationTarget.Kind.CLASS)
            .filter(annotation -> filter.test(annotation.target().asClass()))
            .sorted(
                Comparator.comparing(annotation -> annotation.target().asClass().name().toString()))
            .map(annotation -> new IndexedAnnotation(annotation, resource))
            .forEach(matches::add);
      } catch (UnsupportedVersion | IllegalArgumentException e) {
        throw exceptionFactory.apply("Unable to read Jandex index " + resource.url(), e);
      }
    }
    return List.copyOf(matches);
  }

  public static List<IndexedResource> readIndexes(
      List<ClassLoader> loaders,
      BiFunction<String, Exception, ? extends RuntimeException> exceptionFactory) {
    var indexes = new ArrayList<IndexedResource>();
    var visitedUrls = new HashSet<String>();
    for (ClassLoader loader : loaders) {
      var resources = findResources(loader, exceptionFactory);
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();
        if (visitedUrls.add(url.toString())) {
          readIndex(url).ifPresent(index -> indexes.add(new IndexedResource(url, loader, index)));
        }
      }
    }
    return List.copyOf(indexes);
  }

  private static Enumeration<URL> findResources(
      ClassLoader loader,
      BiFunction<String, Exception, ? extends RuntimeException> exceptionFactory) {
    try {
      return loader.getResources(INDEX_RESOURCE);
    } catch (IOException e) {
      throw exceptionFactory.apply("Unable to find Jandex indexes", e);
    }
  }

  private static Optional<IndexView> readIndex(URL url) {
    try (var input = url.openStream()) {
      return Optional.of(new IndexReader(input).read());
    } catch (IOException | UnsupportedVersion | IllegalArgumentException e) {
      log.warn(
          "Skipping unreadable Jandex index {}. Extensions in this index will not be discovered.",
          url,
          e);
      return Optional.empty();
    }
  }

  public record IndexedResource(URL url, ClassLoader classLoader, IndexView index) {}

  public record IndexedAnnotation(AnnotationInstance annotation, IndexedResource resource) {}
}
