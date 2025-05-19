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

package de.gematik.test.tiger;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;

@Getter
public class RbelLoggerWriter {
  private final RbelLogger rbelLogger;
  private final RbelWriter rbelWriter;

  public RbelLoggerWriter() {
    this.rbelLogger = buildRbelLogger();
    this.rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
  }

  public RbelConverter getRbelConverter() {
    return rbelLogger.getRbelConverter();
  }

  private RbelLogger buildRbelLogger() {
    return RbelLogger.build(
        RbelConfiguration.builder()
            .initializers(
                listKeyFolders().stream()
                    .map(RbelKeyFolderInitializer::new)
                    .map(init -> (Consumer<RbelConverter>) init)
                    .toList())
            .build()
            .activateConversionFor("asn1"));
  }

  @SuppressWarnings("unchecked")
  private List<String> listKeyFolders() {
    return TigerGlobalConfiguration.instantiateConfigurationBean(
            List.class, "tiger.tigerProxy.keyFolders")
        .orElse(List.of());
  }
}
