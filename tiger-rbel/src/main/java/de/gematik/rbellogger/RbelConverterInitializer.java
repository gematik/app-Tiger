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
package de.gematik.rbellogger;

import de.gematik.rbellogger.RbelConverterIndex.ConverterDescriptor;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.exceptions.RbelInitializationException;
import de.gematik.rbellogger.facets.asn1.RbelAsn1Converter;
import de.gematik.rbellogger.facets.cetp.RbelCetpConverter;
import de.gematik.rbellogger.facets.http.RbelAuthorizationHeaderConverter;
import de.gematik.rbellogger.facets.http.RbelHttpRequestConverter;
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import de.gematik.rbellogger.facets.http.formdata.RbelHttpFormDataConverter;
import de.gematik.rbellogger.facets.jackson.RbelCborConverter;
import de.gematik.rbellogger.facets.jackson.RbelJsonConverter;
import de.gematik.rbellogger.facets.jose.RbelJweConverter;
import de.gematik.rbellogger.facets.jose.RbelJwtConverter;
import de.gematik.rbellogger.facets.pki.RbelX500Converter;
import de.gematik.rbellogger.facets.pki.RbelX509Converter;
import de.gematik.rbellogger.facets.sicct.RbelSicctEnvelopeConverter;
import de.gematik.rbellogger.facets.uri.RbelUriConverter;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaKeyDeriver;
import de.gematik.rbellogger.facets.xml.RbelMtomConverter;
import de.gematik.rbellogger.facets.xml.RbelXmlConverter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class RbelConverterInitializer {

  private final RbelConverter rbelConverter;
  private final RbelConfiguration rbelConfiguration;
  private final List<String> activateRbelParsingFor;
  private final Set<ConverterDescriptor> leftovers = new LinkedHashSet<>();
  private final List<ConverterDescriptor> converters = new ArrayList<>();
  private final Set<String> addedConverterNames = new HashSet<>();

  public void addConverters() {
    final List<ConverterDescriptor> candidates = findAllAvailableConverters();
    rbelConverter.setKnownOptionalParsers(collectParserIdentifiers(candidates));

    // find the correct list in correct order
    for (ConverterDescriptor converter : candidates) {
      if (!converter.addAutomatically() || isOptionalAndNotActivated(converter)) {
        continue;
      }
      if (isAnyDependencyMissingForThisConverter(converter)) {
        log.atTrace().addArgument(converter::className).log("Adding {} to leftovers");
        leftovers.add(converter);
      } else {
        addToConverters(converter);
      }
    }

    // add the leftovers
    checkAndAddLeftoverConverters();

    log.atTrace()
        .addArgument(
            () ->
                converters.stream()
                    .map(ConverterDescriptor::className)
                    .collect(Collectors.joining("\n")))
        .log("Final converter list is {}");

    // finally instantiate and add them to the rbelConverter
    for (ConverterDescriptor converter : converters) {
      buildConverterInstance(converter.loadClass()).ifPresent(rbelConverter::addConverter);
    }
  }

  /** Every parser id declared anywhere on the classpath, whether activated or not. */
  private static Set<String> collectParserIdentifiers(List<ConverterDescriptor> converters) {
    return converters.stream()
        .map(ConverterDescriptor::onlyActivateFor)
        .flatMap(List::stream)
        .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
  }

  private void addToConverters(ConverterDescriptor converter) {
    converters.add(converter);
    addedConverterNames.add(converter.className());
  }

  private Optional<RbelConverterPlugin> buildConverterInstance(
      Class<? extends RbelConverterPlugin> converterClass) {
    try {
      return Optional.of(
          converterClass
              .getDeclaredConstructor(RbelConfiguration.class)
              .newInstance(rbelConfiguration));
    } catch (NoSuchMethodException e) {
      try {
        return Optional.of(converterClass.getDeclaredConstructor().newInstance());
      } catch (Exception innerException) {
        if (converterClass.isAnonymousClass()) {
          return Optional.empty();
        } else {
          throw new RbelInitializationException(
              "Could not initialize the converters. Error for '" + converterClass.getName() + "'",
              innerException);
        }
      }
    } catch (Exception e) {
      throw new RbelInitializationException(
          "Could not initialize the converters. Error for '" + converterClass.getName() + "'", e);
    }
  }

  private boolean isOptionalAndNotActivated(ConverterDescriptor converter) {
    if (converter.onlyActivateFor().isEmpty()) {
      return false;
    }
    if (converter.onlyActivateFor().stream()
        .noneMatch(
            item ->
                activateRbelParsingFor.stream()
                    .anyMatch(activated -> activated.equalsIgnoreCase(item)))) {
      log.atTrace().addArgument(converter::className).log("SKIPPING optional converter {}");
      return true;
    }
    log.atTrace().addArgument(converter::className).log("ADDING optional converter {}");
    return false;
  }

  static List<ConverterDescriptor> findAllAvailableConverters() {
    var seeds =
        List.of(
            RbelUriConverter.class,
            RbelHttpResponseConverter.class,
            RbelHttpRequestConverter.class,
            RbelJwtConverter.class,
            RbelHttpFormDataConverter.class,
            RbelJweConverter.class,
            RbelAuthorizationHeaderConverter.class,
            RbelXmlConverter.class,
            RbelJsonConverter.class,
            RbelVauEpaKeyDeriver.class,
            RbelMtomConverter.class,
            RbelX509Converter.class,
            RbelX500Converter.class,
            RbelSicctEnvelopeConverter.class,
            RbelCetpConverter.class,
            RbelCborConverter.class,
            RbelAsn1Converter.class);
    Map<String, ConverterDescriptor> indexed = new LinkedHashMap<>();
    RbelConverterIndex.readIndex().forEach(d -> indexed.put(d.className(), d));

    Map<String, ConverterDescriptor> result = new LinkedHashMap<>();
    for (Class<? extends RbelConverterPlugin> seed : seeds) {
      result.put(
          seed.getName(),
          Optional.ofNullable(indexed.get(seed.getName()))
              .orElseGet(
                  () -> {
                    throw new RbelInitializationException(
                        "Missing converter "
                            + seed.getName()
                            + " in "
                            + RbelConverterIndex.INDEX_RESOURCE
                            + ". Build the module with jandex-maven-plugin.");
                  }));
    }
    indexed.forEach(result::putIfAbsent);
    return new ArrayList<>(result.values());
  }

  private void checkAndAddLeftoverConverters() {
    boolean wasAnyConverterAdded;
    int iterationsLeft = 1000;
    do {
      wasAnyConverterAdded = false;
      log.trace("Checking leftovers {}...", leftovers);
      for (ConverterDescriptor converter : new ArrayList<>(leftovers)) {
        if (isAnyDependencyMissingForThisConverter(converter)) {
          continue;
        }
        addToConverters(converter);
        leftovers.remove(converter);
        wasAnyConverterAdded = true;
        if (iterationsLeft-- <= 0) {
          throw new RbelInitializationException(
              "Could not instantiate '" + converter.className() + "' due to dependencies.");
        }
      }
    } while (wasAnyConverterAdded);
  }

  private boolean isAnyDependencyMissingForThisConverter(ConverterDescriptor converter) {
    return !addedConverterNames.containsAll(converter.dependsOn());
  }
}
