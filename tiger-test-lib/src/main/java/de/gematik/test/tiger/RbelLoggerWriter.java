/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.lib.TigerDirector;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Getter;

@Getter
public class RbelLoggerWriter {
  private RbelLogger rbelLogger;
  private RbelWriter rbelWriter;

  public RbelLoggerWriter() {
    assureRbelIsInitialized();
  }

  public RbelConverter getRbelConverter() {
    return rbelLogger.getRbelConverter();
  }

  private synchronized void assureRbelIsInitialized() {
    if (rbelWriter == null) {
      final List<String> keyFolders;
      if (TigerDirector.isInitialized()) {
        keyFolders =
            TigerDirector.getTigerTestEnvMgr().getConfiguration().getTigerProxy().getKeyFolders();
      } else {
        keyFolders = null;
      }
      rbelLogger =
          RbelLogger.build(
              RbelConfiguration.builder()
                  .activateAsn1Parsing(true)
                  .initializers(
                      Optional.ofNullable(keyFolders).stream()
                          .flatMap(List::stream)
                          .map(RbelKeyFolderInitializer::new)
                          .map(init -> (Consumer<RbelConverter>) init)
                          .toList())
                  .build());
      rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
    }
  }
}
