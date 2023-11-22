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

package de.gematik.test.tiger.maven.adapter.mojos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.restassured.RestAssured;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the maven plugin to start up a test environment. Test setup is that in
 * tiger-integration-tests/tiger-maven-plugin-integration-tests.Jenkinsfile we do run the maven
 * plugin once with setup-testenv that starts a winstone server on free.port.0 and logs the output
 * to log.txt In parallel we do run this test as a second maven job, check for log.txt and try to
 * retrieve the health check url of the started winstone server. Finally, we check that we do get a
 * status 200 back and all is fine.
 */
@Slf4j
public class EnvironmentMojoIT {

  private static String winstoneUrl;

  @BeforeAll
  public static void parseWinstoneUrl() {
    log.info("Waiting for setup-testenv run to log out winstone healthcheck url...");
    File f = new File("log.txt");
    await()
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (f.exists()) {
                String log = IOUtils.toString(f.toURI(), StandardCharsets.UTF_8);
                if (log.contains("Waiting for URL")) {
                  int i = log.indexOf("Waiting for URL");
                  int start = log.indexOf("'", i);
                  int end = log.indexOf("'", start + 1);
                  if (start != -1 && end != -1) {
                    winstoneUrl = log.substring(start + 1, end);
                    return true;
                  }
                }
              }
              return false;
            });
    log.info("Assuming winstone at {}", winstoneUrl);
  }

  @SneakyThrows
  @Test
  void checkTestenvIsUp() {
    assertThat(RestAssured.get(new URL(winstoneUrl)).getStatusCode()).isEqualTo(200);
    log.info("Reached winstone server at {} and got return status 200", winstoneUrl);
  }
}
