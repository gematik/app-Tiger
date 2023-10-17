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

package de.gematik.test.tiger.lib.reports;

import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class TigerRestAssuredCurlLoggingFilter implements Filter {

    private ByteArrayOutputStream outputStream;
    private RequestLoggingFilter requestLoggingFilter;

    public TigerRestAssuredCurlLoggingFilter() {
        outputStream = new ByteArrayOutputStream();
        requestLoggingFilter = new RequestLoggingFilter(
            LogDetail.ALL,
            true,
            new PrintStream(outputStream),
            true);
    }

    public synchronized void printToReport() {
        String raLog = outputStream.toString(StandardCharsets.UTF_8);
        outputStream.reset();

        if (raLog.isEmpty()) {
            return;
        }
        int callCounter = 0;
        final List<String> listOfCurlCalls = RestAssuredLogToCurlCommandParser.convertRestAssuredLogToCurlCalls(raLog);
        for (String callLog : listOfCurlCalls) {
            String curlCommand = RestAssuredLogToCurlCommandParser.parseCurlCommandFromRestAssuredLog(callLog);
            if (TigerDirector.isSerenityAvailable(true) && !curlCommand.isEmpty()) {
                String title = "cURL";
                if (listOfCurlCalls.size() > 1) {
                    title += " " + String.format("%3d", callCounter++); // 3 digit zero padded counter string
                }
                log.debug("RestAssured details for cURL command:\n{}", callLog);
                SerenityReportUtils.addCustomData(title, curlCommand);
            }
        }
    }

    @Override
    public synchronized Response filter(FilterableRequestSpecification requestSpec,
        FilterableResponseSpecification responseSpec,
        FilterContext ctx) {
        return requestLoggingFilter.filter(requestSpec, responseSpec, ctx);
    }
}
