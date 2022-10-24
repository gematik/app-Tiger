/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger;

import de.gematik.rbellogger.data.RbelHostname;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class TestUtils {

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
