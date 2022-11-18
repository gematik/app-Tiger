/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.util;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.TestUtils;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelAnsiColors;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class RbelElementTreePrinterTest {

    @Test
    void printFacets() throws IOException {
        RbelOptions.activateFacetsPrinting();
        RbelAnsiColors.deactivateAnsiColors();

        final String treeStructure = TestUtils.readAndConvertCurlMessage(
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
        RbelAnsiColors.deactivateAnsiColors();

        final String responseCodeTree = TestUtils.readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl")
            .findElement(rbelPathSelector).get()
            .printTreeStructure();

        assertThat(responseCodeTree)
            .contains(" (" + expectedValueInPrintedTree + ")")
            .contains("──" + expectedKeyInPrintedTree + " ");
    }
}

