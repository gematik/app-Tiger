/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.util;

import static de.gematik.rbellogger.TestUtils.readAndConvertCurlMessage;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelOptions;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RbelElementTreePrinterTest {

    @Test
    void printFacets() throws IOException {
        RbelOptions.activateFacetsPrinting();
        RbelOptions.deactivateAnsiColors();

        final String treeStructure = readAndConvertCurlMessage(
            "src/test/resources/sampleMessages/xmlMessage.curl").printTreeStructure();
        System.out.println(treeStructure);
        assertThat(treeStructure)
            .isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"$.responseCode,200, responseCode",
        "$..authorization_endpoint.content, http://localhost:8080/sign_response, content",
        "$..authorization_endpoint.content.basicPath, http://localhost:8080/sign_response, basicPath"})
    void printValue_shouldContainValue(String rbelPathSelector, String expectedValueInPrintedTree, String expectedKeyInPrintedTree) throws IOException {
        RbelOptions.deactivateAnsiColors();

        final String responseCodeTree = readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl")
            .findElement(rbelPathSelector).get()
            .printTreeStructure();

        assertThat(responseCodeTree)
            .contains(" (" + expectedValueInPrintedTree + ")")
            .contains("──" + expectedKeyInPrintedTree + " ");
    }
}