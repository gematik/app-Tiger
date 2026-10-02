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
package de.gematik.test.tiger.lib.reports;

import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public class TigerRestAssuredCurlLoggingFilter implements Filter {

  @Getter
  private final List<String> recordedCurlCommands = Collections.synchronizedList(new ArrayList<>());

  public synchronized void printToReport() {
    if (recordedCurlCommands.isEmpty()) {
      return;
    }
    int callCounter = 0;
    List<String> commandsToPublish = new ArrayList<>(recordedCurlCommands);
    recordedCurlCommands.clear();

    for (String curlCommand : commandsToPublish) {
      if (TigerDirector.isSerenityAvailable(true) && !curlCommand.isEmpty()) {
        String title = "cURL";
        if (commandsToPublish.size() > 1) {
          title += " " + String.format("%3d", callCounter++); // 3 digit space padded counter string
        }
        log.debug("RestAssured details for cURL command:\n{}", curlCommand);
        SerenityReportUtils.addCustomData(title, curlCommand);
      }
    }
  }

  @Override
  public Response filter(
      FilterableRequestSpecification requestSpec,
      FilterableResponseSpecification responseSpec,
      FilterContext ctx) {
    try {
      String curlCmd = RestAssuredToCurlConverter.toCurl(requestSpec);
      recordedCurlCommands.add(curlCmd);
    } catch (Exception e) {
      log.warn("Failed to generate cURL command from RestAssured request", e);
    }
    return ctx.next(requestSpec, responseSpec);
  }
}
