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

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.*;
import de.gematik.rbellogger.exceptions.RbelInitializationException;
import de.gematik.rbellogger.facets.asn1.RbelAsn1Converter;
import de.gematik.rbellogger.facets.cetp.RbelCetpConverter;
import de.gematik.rbellogger.facets.http.RbelBearerTokenConverter;
import de.gematik.rbellogger.facets.http.RbelHttpRequestConverter;
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import de.gematik.rbellogger.facets.http.formdata.RbelHttpFormDataConverter;
import de.gematik.rbellogger.facets.jackson.RbelCborConverter;
import de.gematik.rbellogger.facets.jackson.RbelJsonConverter;
import de.gematik.rbellogger.facets.jose.RbelJweConverter;
import de.gematik.rbellogger.facets.jose.RbelJwtConverter;
import de.gematik.rbellogger.facets.mime.RbelCmsEnvelopedDataConverter;
import de.gematik.rbellogger.facets.mime.RbelMimeConverter;
import de.gematik.rbellogger.facets.pki.RbelPkcs7Converter;
import de.gematik.rbellogger.facets.pki.RbelX500Converter;
import de.gematik.rbellogger.facets.pki.RbelX509Converter;
import de.gematik.rbellogger.facets.pop3.RbelPop3CommandConverter;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseConverter;
import de.gematik.rbellogger.facets.sicct.RbelSicctCommandConverter;
import de.gematik.rbellogger.facets.sicct.RbelSicctEnvelopeConverter;
import de.gematik.rbellogger.facets.smtp.RbelSmtpCommandConverter;
import de.gematik.rbellogger.facets.smtp.RbelSmtpResponseConverter;
import de.gematik.rbellogger.facets.uri.RbelUriConverter;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaKeyDeriver;
import de.gematik.rbellogger.facets.xml.RbelMtomConverter;
import de.gematik.rbellogger.facets.xml.RbelXmlConverter;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Slf4j
@AllArgsConstructor
public class RbelConverterInitializer {

  private final RbelConverter rbelConverter;
  private final RbelConfiguration rbelConfiguration;
  private final List<String> activateRbelParsingFor;
  private final Set<Class<? extends RbelConverterPlugin>> leftovers = new HashSet<>();
  private final List<Class<? extends RbelConverterPlugin>> converters = new ArrayList<>();

  public void addConverters() {
    final List<Class<? extends RbelConverterPlugin>> subclasses = findAllAvailableConverters();

    // find the correct list in correct order
    for (Class<? extends RbelConverterPlugin> converterClass : subclasses) {
      if (Modifier.isAbstract(converterClass.getModifiers())) {
        continue;
      }
      if (converterClass.isAnnotationPresent(ConverterInfo.class)) {
        ConverterInfo converterInfo = converterClass.getAnnotation(ConverterInfo.class);
        if ((!converterInfo.addAutomatically()) || isOptionalAndNotActivated(converterClass)) {
          continue;
        }

        if (isAnyDependencyMissingForThisConverter(converterInfo)) {
          log.atTrace().addArgument(converterClass::getSimpleName).log("Adding {} to leftovers");
          leftovers.add(converterClass);
          continue;
        } else {
          log.atTrace()
              .addArgument(converterClass::getSimpleName)
              .log("NOT adding {} to leftovers");
        }
      }
      converters.add(converterClass);
    }

    // add the leftovers
    checkAndAddLeftoverConverters(leftovers);

    log.atTrace()
        .addArgument(
            () -> converters.stream().map(Class::getSimpleName).collect(Collectors.joining("\n")))
        .log("Final converter list is {}");

    // finally instantiate and add them to the rbelConverter
    for (Class<? extends RbelConverterPlugin> converterClass : converters) {
      buildConverterInstance(converterClass).ifPresent(rbelConverter::addConverter);
    }
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

  private boolean isOptionalAndNotActivated(Class<? extends RbelConverterPlugin> converterClass) {
    ConverterInfo converterInfo = converterClass.getAnnotation(ConverterInfo.class);
    if (converterInfo.onlyActivateFor() != null && converterInfo.onlyActivateFor().length > 0) {
      if (Arrays.stream(converterInfo.onlyActivateFor())
          .noneMatch(
              item ->
                  activateRbelParsingFor.stream()
                      .anyMatch(activated -> activated.equalsIgnoreCase(item)))) {
        log.atTrace()
            .addArgument(converterClass::getSimpleName)
            .log("SKIPPING optional converter {}");
        return true;
      } else {
        log.atTrace()
            .addArgument(converterClass::getSimpleName)
            .log("ADDING optional converter {}");
      }
    }
    return false;
  }

  private static List<Class<? extends RbelConverterPlugin>> findAllAvailableConverters() {
    var initialList =
        new ArrayList<>(
            List.of(
                RbelUriConverter.class,
                RbelHttpResponseConverter.class,
                RbelHttpRequestConverter.class,
                RbelJwtConverter.class,
                RbelHttpFormDataConverter.class,
                RbelJweConverter.class,
                RbelBearerTokenConverter.class,
                RbelXmlConverter.class,
                RbelJsonConverter.class,
                RbelVauEpaKeyDeriver.class,
                RbelMtomConverter.class,
                RbelX509Converter.class,
                RbelX500Converter.class,
                RbelSicctEnvelopeConverter.class,
                RbelSicctCommandConverter.class,
                RbelCetpConverter.class,
                RbelCborConverter.class,
                RbelPop3CommandConverter.class,
                RbelPop3ResponseConverter.class,
                RbelMimeConverter.class,
                RbelCmsEnvelopedDataConverter.class,
                RbelPkcs7Converter.class,
                RbelSmtpCommandConverter.class,
                RbelSmtpResponseConverter.class,
                RbelAsn1Converter.class));
    Reflections reflections = new Reflections("de.gematik");
    reflections.getSubTypesOf(RbelConverterPlugin.class).stream()
        .filter(c -> !initialList.contains(c))
        .filter(c -> !c.isAnonymousClass())
        .forEach(initialList::add);
    return initialList;
  }

  private void checkAndAddLeftoverConverters(Set<Class<? extends RbelConverterPlugin>> leftovers) {
    boolean wasAnyConverterAdded;
    int iterationsLeft = 1000;
    do {
      wasAnyConverterAdded = false;
      log.trace("Checking leftovers {}...", leftovers);
      for (Class<? extends RbelConverterPlugin> converterClass : new HashSet<>(leftovers)) {
        ConverterInfo converterInfo = converterClass.getAnnotation(ConverterInfo.class);
        if (isAnyDependencyMissingForThisConverter(converterInfo)) {
          continue;
        }
        converters.add(converterClass);
        leftovers.remove(converterClass);
        wasAnyConverterAdded = true;
        if (iterationsLeft-- <= 0) {
          throw new RbelInitializationException(
              "Could not instantiate '" + converterClass.getName() + "' due to dependencies.");
        }
      }
    } while (wasAnyConverterAdded);
  }

  private boolean isAnyDependencyMissingForThisConverter(ConverterInfo converterInfo) {
    return converterInfo.dependsOn() != null
        && converterInfo.dependsOn().length > 0
        && !new HashSet<>(converters).containsAll(Arrays.asList(converterInfo.dependsOn()));
  }
}
