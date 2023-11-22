/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.data.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.TestUtils;
import de.gematik.rbellogger.util.RbelAnsiColors;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RbelElementTreePrinterTest {

  @Test
  void printFacets() throws IOException {
    RbelOptions.activateFacetsPrinting();
    RbelAnsiColors.deactivateAnsiColors();

    final String treeStructure =
        TestUtils.readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl")
            .printTreeStructure();
    System.out.println(treeStructure);
    assertThat(treeStructure).isNotBlank();
  }

  @ParameterizedTest
  @CsvSource({
    "$.responseCode,200, responseCode",
    "$..authorization_endpoint.content, http://localhost:8080/sign_response, content",
    "$..authorization_endpoint.content.basicPath, http://localhost:8080/sign_response, basicPath"
  })
  void printValue_shouldContainValue(
      String rbelPathSelector, String expectedValueInPrintedTree, String expectedKeyInPrintedTree)
      throws IOException {
    RbelAnsiColors.deactivateAnsiColors();

    final String responseCodeTree =
        TestUtils.readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl")
            .findElement(rbelPathSelector)
            .get()
            .printTreeStructure();

    assertThat(responseCodeTree)
        .contains(" (" + expectedValueInPrintedTree + ")")
        .contains("──" + expectedKeyInPrintedTree + " ");
  }
}
