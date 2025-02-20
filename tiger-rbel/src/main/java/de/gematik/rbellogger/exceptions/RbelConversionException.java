/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.exceptions;

import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.Base64;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
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
      String message, RbelElement currentElement, RbelConverterPlugin plugin) {
    super(message);
    this.currentElement = currentElement;
    this.converter = plugin;
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
    log.atInfo().log(this::generateGenericConversionErrorMessage);
    log.debug("Stack trace", this);
    if (log.isDebugEnabled()) {
      log.debug(
          "Content in failed conversion-attempt was (B64-encoded) {}",
          Base64.getEncoder().encodeToString(currentElement.getRawContent()));
      if (currentElement.getParentNode() != null) {
        log.debug(
            "Parent-Content in failed conversion-attempt was (B64-encoded) {}",
            Base64.getEncoder().encodeToString(currentElement.getParentNode().getRawContent()));
      }
    }
  }

  private String generateGenericConversionErrorMessage() {
    return "Exception during conversion with plugin '"
        + converter.getClass().getSimpleName()
        + "' ("
        + getMessage()
        + ")";
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
