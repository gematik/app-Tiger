/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
import jakarta.servlet.ServletContextListener;
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
    new SpringApplicationBuilder()
        .bannerMode(Mode.OFF)
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
