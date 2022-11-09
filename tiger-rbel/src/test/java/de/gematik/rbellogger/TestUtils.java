/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Function;
import org.apache.commons.io.FileUtils;

public class TestUtils {

    public static RbelElement readAndConvertCurlMessage(String fileName, Function<String, String>... messageMappers) throws IOException {
        String curlMessage = readCurlFromFileWithCorrectedLineBreaks(fileName);
        for (Function<String, String> mapper : messageMappers) {
            curlMessage = mapper.apply(curlMessage);
        }
        return RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
    }

    public static String readCurlFromFileWithCorrectedLineBreaks(String fileName) throws IOException {
        return readCurlFromFileWithCorrectedLineBreaks(fileName, Charset.defaultCharset());
    }

    public static String readCurlFromFileWithCorrectedLineBreaks(String fileName, Charset charset) throws IOException {
        return FileUtils.readFileToString(new File(fileName), charset)
            .replaceAll("(?<!\\r)\\n", "\r\n");
    }

    public static RbelHostname localhostWithPort(int tcpPort) {
        return RbelHostname.builder()
            .hostname("localhost")
            .port(tcpPort)
            .build();
    }
}
