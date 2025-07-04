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
package de.gematik.test.tiger.testenvmgr;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import jakarta.servlet.ServletContextListener;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    scanBasePackageClasses = {TigerBuildPropertiesService.class, TigerTestEnvMgrApplication.class})
@Slf4j
public class TigerTestEnvMgrApplication implements ServletContextListener {

  public static void main(String[] args) {
    Map<String, Object> properties = TigerTestEnvMgr.getConfiguredLoggingLevels();
    properties.put("spring.mustache.enabled", false); // TGR-875 avoid warning in console
    properties.put("spring.mustache.check-template-location", false);
    properties.putAll(TigerTestEnvMgr.getTigerLibConfiguration());
    new SpringApplicationBuilder()
        .bannerMode(Mode.OFF)
        .properties(properties)
        .sources(TigerTestEnvMgrApplication.class)
        .initializers()
        .run(args);
  }

  @Bean
  public TigerTestEnvMgr tigerTestEnvMgr() {
    TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
    log.info(
        Ansi.colorize("Tiger standalone test environment manager UP!", RbelAnsiColors.GREEN_BOLD));
    return envMgr;
  }
}
