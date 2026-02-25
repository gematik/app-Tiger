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
package de.gematik.rbellogger.facets.vau.asl;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.vau.AbstractAslDecryptionConverter;
import lombok.extern.slf4j.Slf4j;

@ConverterInfo(onlyActivateFor = "asl")
@Slf4j
public class RbelAslConverter extends AbstractAslDecryptionConverter {

  @Override
  public String getKeyHeaderName() {
    return "ZETA-ASL-nonPU-Tracing";
  }

  public RbelAslEncryptionFacet buildFacet(
      RbelElement cleartextElement, RbelElement headerElement) {
    return new RbelAslEncryptionFacet(cleartextElement, headerElement);
  }

  @Override
  public void consumeElement(RbelElement element, RbelConversionExecutor context) {
    context.waitForAllElementsBeforeGivenToBeParsed(element.findRootElement());
    if (element.getParentNode() != null
        && element.getParentNode().hasFacet(RbelHttpMessageFacet.class)) {
      tryToExtractVauNonPuTracingKeys(element, context);
      tryToParseVau3AslMessage(element, context);
    }
  }
}
