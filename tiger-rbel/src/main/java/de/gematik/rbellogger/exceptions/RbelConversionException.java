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
package de.gematik.rbellogger.exceptions;

import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.Base64;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

@Getter
@EqualsAndHashCode(callSuper = true)
public class RbelConversionException extends GenericTigerException {

  @Setter private RbelElement currentElement;
  @Setter private RbelConverterPlugin converter;

  public RbelConversionException(String s) {
    super(s);
  }

  public RbelConversionException(Exception e) {
    super(e);
  }

  public RbelConversionException(String msg, Throwable e) {
    super(msg, e);
  }

  public RbelConversionException(Exception e, RbelElement value) {
    super(e);
    this.currentElement = value;
  }

  public RbelConversionException(
      Exception e, RbelElement currentElement, RbelConverterPlugin plugin) {
    super(e);
    this.currentElement = currentElement;
    this.converter = plugin;
  }

  public RbelConversionException(
      String msg, Exception e, RbelElement currentElement, RbelConverterPlugin plugin) {
    super(msg, e);
    this.currentElement = currentElement;
    this.converter = plugin;
  }

  public RbelConversionException(
      String message, RbelElement currentElement, RbelConverterPlugin plugin) {
    super(message);
    this.currentElement = currentElement;
    this.converter = plugin;
  }

  public RbelConversionException(String msg, Exception e, RbelElement value) {
    super(msg, e);
    this.currentElement = value;
  }

  public static RbelConversionException wrapIfNotAConversionException(
      Exception input, RbelConverterPlugin plugin, RbelElement currentElement) {
    if (input instanceof RbelConversionException conversionException) {
      conversionException.setCurrentElement(currentElement);
      conversionException.setConverter(plugin);
      return conversionException;
    } else {
      return new RbelConversionException(input, currentElement, plugin);
    }
  }

  public void printDetailsToLog(Logger log) {
    log.atDebug().log(this::generateGenericConversionErrorMessage);
    log.debug("Stack trace", this);
    if (log.isTraceEnabled()) {
      log.trace(
          "Content in failed conversion-attempt was {} bytes: {}",
          currentElement.getRawContent().length,
          currentElement.getRawStringContent());
      if (currentElement.getParentNode() != null) {
        log.trace(
            "Parent-Content in failed conversion-attempt was (B64-encoded) {}",
            Base64.getEncoder().encodeToString(currentElement.getParentNode().getRawContent()));
      }
    }
  }

  private String generateGenericConversionErrorMessage() {
    return "Exception during conversion with plugin '"
        + chooseAppropriateScreenNameForConverter()
        + "' ("
        + getMessage()
        + ")";
  }

  private String chooseAppropriateScreenNameForConverter() {
    if (StringUtils.isEmpty(converter.getClass().getSimpleName())) {
      return converter.getClass().getName();
    } else {
      return converter.getClass().getSimpleName();
    }
  }

  public void addErrorNoteFacetToElement() {
    if (currentElement == null) {
      return;
    }
    currentElement.addFacet(
        new RbelNoteFacet(
            generateGenericConversionErrorMessage() + "\n\n" + ExceptionUtils.getStackTrace(this),
            RbelNoteFacet.NoteStyling.ERROR));
  }
}
