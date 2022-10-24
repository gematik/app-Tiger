/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.util;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.function.Function;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

public class RbelElementTreePrinterTest {

    @Test
    public void printFacets() throws IOException {
        RbelOptions.activateFacetsPrinting();
        RbelOptions.deactivateAnsiColors();

        System.out.println(readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl").printTreeStructure());
    }

    @ParameterizedTest
    @CsvSource({"$.responseCode,200, responseCode",
        "$..authorization_endpoint.content, http://localhost:8080/sign_response, content",
        "$..authorization_endpoint.content.basicPath, http://localhost:8080/sign_response, basicPath"})
    public void printValue_shouldContainValue(String rbelPathSelector, String expectedValueInPrintedTree, String expectedKeyInPrintedTree) throws IOException {
        RbelOptions.deactivateAnsiColors();

        final String responseCodeTree = readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl")
            .findElement(rbelPathSelector).get()
            .printTreeStructure();

        assertThat(responseCodeTree)
            .contains(" (" + expectedValueInPrintedTree + ")")
            .contains("──" + expectedKeyInPrintedTree + " ");
    }

    private static RbelElement readAndConvertCurlMessage(String fileName, Function<String, String>... messageMappers) throws IOException {
        String curlMessage = readCurlFromFileWithCorrectedLineBreaks(fileName);
        for (Function<String, String> mapper : messageMappers) {
            curlMessage = mapper.apply(curlMessage);
        }
        return RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
    }
}