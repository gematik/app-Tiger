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

import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.exceptions.RbelInitializationException;
import de.gematik.test.tiger.common.util.JandexIndexReader;
import java.lang.reflect.Modifier;
import java.util.*;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/** Discovers converters from the Jandex indexes shipped with their JARs. */
public final class RbelConverterIndex {

  public static final String INDEX_RESOURCE = JandexIndexReader.INDEX_RESOURCE;
  private static final DotName CONVERTER =
      DotName.createSimple(RbelConverterPlugin.class.getName());
  private static final DotName INFO = DotName.createSimple(ConverterInfo.class.getName());

  private RbelConverterIndex() {}

  /** All indexed converters, sorted by class name. */
  public static List<ConverterDescriptor> readIndex() {
    return readIndex(JandexIndexReader.classLoaders(RbelConverterIndex.class));
  }

  static List<ConverterDescriptor> readIndex(List<ClassLoader> classLoaders) {
    List<IndexView> indexes = new ArrayList<>();
    Map<String, ClassLoader> origins = new HashMap<>();
    for (var resource :
        JandexIndexReader.readIndexes(classLoaders, RbelInitializationException::new)) {
      indexes.add(resource.index());
      resource
          .index()
          .getKnownClasses()
          .forEach(c -> origins.putIfAbsent(c.name().toString(), resource.classLoader()));
    }
    IndexView index = CompositeIndex.create(indexes);
    return index.getAllKnownSubclasses(CONVERTER).stream()
        .filter(c -> Modifier.isPublic(c.flags()))
        .filter(c -> !c.isAbstract() && !c.isInterface())
        .filter(c -> c.nestingType() != ClassInfo.NestingType.ANONYMOUS)
        .sorted(Comparator.comparing(c -> c.name().toString()))
        .map(c -> ConverterDescriptor.fromIndex(c, index, origins.get(c.name().toString())))
        .toList();
  }

  public record ConverterDescriptor(
      String className,
      boolean addAutomatically,
      List<String> onlyActivateFor,
      List<String> dependsOn,
      ClassLoader classLoader) {

    static ConverterDescriptor fromIndex(ClassInfo type, IndexView index, ClassLoader classLoader) {
      AnnotationInstance info = type.declaredAnnotation(INFO);
      if (info == null) {
        return new ConverterDescriptor(
            type.name().toString(), true, List.of(), List.of(), classLoader);
      }
      return new ConverterDescriptor(
          type.name().toString(),
          info.valueWithDefault(index, "addAutomatically").asBoolean(),
          List.of(info.valueWithDefault(index, "onlyActivateFor").asStringArray()),
          Arrays.stream(info.valueWithDefault(index, "dependsOn").asClassArray())
              .map(t -> t.name().toString())
              .toList(),
          classLoader);
    }

    public Class<? extends RbelConverterPlugin> loadClass() {
      try {
        return Class.forName(className, false, classLoader).asSubclass(RbelConverterPlugin.class);
      } catch (ClassNotFoundException | ClassCastException e) {
        throw new RbelInitializationException(
            "Unable to load converter '" + className + "' listed in " + INDEX_RESOURCE, e);
      }
    }
  }
}
