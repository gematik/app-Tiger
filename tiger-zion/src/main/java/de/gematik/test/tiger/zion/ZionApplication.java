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

package de.gematik.test.tiger.zion;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.RbelJexlExecutor;
import de.gematik.rbellogger.writer.RbelWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class ZionApplication {

  static {
    RbelJexlExecutor.initialize();
  }

  public static void main(String[] args) { // NOSONAR
    new SpringApplicationBuilder().sources(ZionApplication.class).run(args);
  }

  @Bean
  public RbelLogger rbelLogger() {
    log.info("Starting rbel build...");
    final RbelLogger logger =
        RbelLogger.build(
            new RbelConfiguration()
                .addPostConversionListener(RbelKeyManager.RBEL_IDP_TOKEN_KEY_LISTENER)
                .addInitializer(new RbelKeyFolderInitializer(".")));
    log.info("done with build");
    return logger;
  }

  @Bean
  public RbelWriter rbelWriter(@Autowired RbelLogger rbelLogger) {
    return new RbelWriter(rbelLogger.getRbelConverter());
  }
}
