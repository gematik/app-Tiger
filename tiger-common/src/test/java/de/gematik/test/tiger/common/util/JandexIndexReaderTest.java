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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class JandexIndexReaderTest {

  @TempDir Path temp;

  @Test
  void selectsContextLoaderBeforeOwnerLoaderWithoutDuplicates() {
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    ClassLoader own = getClass().getClassLoader();
    ClassLoader context = new ClassLoader(own) {};
    try {
      thread.setContextClassLoader(context);
      assertThat(JandexIndexReader.classLoaders(getClass())).containsExactly(context, own);
      thread.setContextClassLoader(own);
      assertThat(JandexIndexReader.classLoaders(getClass())).containsExactly(own);
      thread.setContextClassLoader(null);
      assertThat(JandexIndexReader.classLoaders(getClass())).containsExactly(own);
      assertThat(JandexIndexReader.classLoaders(String.class)).isEmpty();
      thread.setContextClassLoader(context);
      assertThat(JandexIndexReader.classLoaders(String.class)).containsExactly(context);
    } finally {
      thread.setContextClassLoader(original);
    }
  }

  @Test
  void selectsClassAnnotationsAndRetainsResourceOrderAndOrigins() throws IOException {
    URL first = writeIndex("annotations-first.idx", Zulu.class, Alpha.class, Hidden.class);
    URL second = writeIndex("annotations-second.idx", Alpha.class);
    ClassLoader firstLoader = loader(first);
    ClassLoader secondLoader = loader(first, second);

    var matches =
        JandexIndexReader.findClassAnnotations(
            List.of(firstLoader, secondLoader),
            DotName.createSimple(Marker.class.getName()),
            type -> Modifier.isPublic(type.flags()),
            IllegalStateException::new);

    assertThat(matches)
        .extracting(match -> match.annotation().target().asClass().name().toString())
        .containsExactly(Alpha.class.getName(), Zulu.class.getName(), Alpha.class.getName());
    assertThat(matches)
        .extracting(match -> match.annotation().value().asString())
        .containsExactly("alpha", "zulu", "alpha");
    assertThat(matches)
        .extracting(match -> match.resource().url())
        .containsExactly(first, first, second);
    assertThat(matches)
        .extracting(match -> match.resource().classLoader())
        .containsExactly(firstLoader, firstLoader, secondLoader);
  }

  @Test
  void annotationSelectionWrapsErrorsWithResourceAndRequestedExceptionType() throws IOException {
    URL resource = writeIndex("annotations.idx", Alpha.class);
    var loaders = List.of(loader(resource));
    var annotationName = DotName.createSimple(Marker.class.getName());

    assertThatThrownBy(
            () ->
                JandexIndexReader.findClassAnnotations(
                    loaders,
                    annotationName,
                    type -> {
                      throw new IllegalArgumentException("invalid selection");
                    },
                    IllegalStateException::new))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("annotations.idx")
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsEachUrlOnceAndPreservesOrderAndFirstLoader() throws IOException {
    URL first = writeIndex("first.idx");
    URL second = writeIndex("second.idx");
    var firstLoader = loader(first);
    var secondLoader = loader(first, second);

    var resources =
        JandexIndexReader.readIndexes(
            List.of(firstLoader, secondLoader), IllegalStateException::new);

    assertThat(resources)
        .extracting(JandexIndexReader.IndexedResource::url)
        .containsExactly(first, second);
    assertThat(resources)
        .extracting(JandexIndexReader.IndexedResource::classLoader)
        .containsExactly(firstLoader, secondLoader);
    assertThat(resources)
        .allSatisfy(
            resource ->
                assertThat(
                        resource.index().getClassByName(DotName.createSimple(getClass().getName())))
                    .isNotNull());
  }

  @Test
  void skipsUnreadableIndexesAndWarnsWhileRetainingValidIndexes() throws IOException {
    URL first = writeIndex("first-valid.idx");
    URL corrupt = Files.writeString(temp.resolve("broken.idx"), "not an index").toUri().toURL();
    byte[] unsupportedBytes = Files.readAllBytes(temp.resolve("first-valid.idx"));
    unsupportedBytes[4] = (byte) 127;
    URL unsupported =
        Files.write(temp.resolve("unsupported.idx"), unsupportedBytes).toUri().toURL();
    URL missing = temp.resolve("missing.idx").toUri().toURL();
    URL last = writeIndex("last-valid.idx");
    var loaders = List.of(loader(first, corrupt, unsupported, missing, last));
    Logger logger = (Logger) LoggerFactory.getLogger(JandexIndexReader.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(JandexIndexReader.readIndexes(loaders, IllegalStateException::new))
          .extracting(JandexIndexReader.IndexedResource::url)
          .containsExactly(first, last);
      assertThat(appender.list)
          .filteredOn(event -> event.getLevel() == Level.WARN)
          .hasSize(3)
          .allSatisfy(
              event -> {
                assertThat(event.getFormattedMessage())
                    .contains("Skipping unreadable Jandex index", "will not be discovered");
                assertThat(event.getThrowableProxy()).isNotNull();
              });
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anySatisfy(message -> assertThat(message).contains(corrupt.toString()))
          .anySatisfy(message -> assertThat(message).contains(unsupported.toString()))
          .anySatisfy(message -> assertThat(message).contains(missing.toString()));
      assertThat(appender.list.get(1).getThrowableProxy().getClassName())
          .isEqualTo("org.jboss.jandex.UnsupportedVersion");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void resourceLookupFailureIsNotSilentlyIgnored() {
    ClassLoader loader =
        new ClassLoader() {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            throw new IOException("lookup failed");
          }
        };
    var loaders = List.of(loader);

    assertThatThrownBy(() -> JandexIndexReader.readIndexes(loaders, IllegalStateException::new))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to find Jandex indexes")
        .hasCauseInstanceOf(IOException.class);
  }

  private URL writeIndex(String name) throws IOException {
    return writeIndex(name, getClass());
  }

  private URL writeIndex(String name, Class<?>... classes) throws IOException {
    Path file = temp.resolve(name);
    try (var output = Files.newOutputStream(file)) {
      new IndexWriter(output).write(Index.of(classes));
    }
    return file.toUri().toURL();
  }

  private ClassLoader loader(URL... urls) {
    return new ClassLoader(getClass().getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        return name.equals(JandexIndexReader.INDEX_RESOURCE)
            ? Collections.enumeration(List.of(urls))
            : super.getResources(name);
      }
    };
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Marker {
    String value();
  }

  @Marker("alpha")
  public static class Alpha {
    @Marker("field")
    public String field;

    @Marker("method")
    public void method() {}
  }

  @Marker("zulu")
  public static class Zulu {}

  @Marker("hidden")
  static class Hidden {}
}
