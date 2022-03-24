/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.reports;

import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TigerRestAssuredCurlLoggingFilter implements Filter {

    private final ByteArrayOutputStream outputStream;
    private final RequestLoggingFilter requestLoggingFilter;

    public TigerRestAssuredCurlLoggingFilter() {
        outputStream = new ByteArrayOutputStream();
        requestLoggingFilter = new RequestLoggingFilter(
            LogDetail.ALL,
            true,
            new PrintStream(outputStream),
            true);
    }

    public synchronized void printToReport() {
        int callCounter = 0;
        String raLog = outputStream.toString(StandardCharsets.UTF_8);
        final List<String> listOfCurlCalls = RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(raLog);
        for (String callLog : listOfCurlCalls) {
            String title = "cURL";
            if (listOfCurlCalls.size() > 1) {
                title = title + String.format("%3d", callCounter++);
            }
            String curlCommand = RestAssuredLogToCurlCommandParser.parseCurlCommandFromRestAssuredLog(callLog);
            log.info("cURL command: " + curlCommand);
            log.debug("RestAssured details:\n" + callLog);
            if (TigerDirector.isSerenityAvailable(true)) {
                SerenityReportUtils.addCustomData(title, curlCommand);
            }
        }
        outputStream.reset();

    }

    @Override
    public synchronized Response filter(FilterableRequestSpecification requestSpec,
        FilterableResponseSpecification responseSpec,
        FilterContext ctx) {
        return requestLoggingFilter.filter(requestSpec, responseSpec, ctx);
    }
}
