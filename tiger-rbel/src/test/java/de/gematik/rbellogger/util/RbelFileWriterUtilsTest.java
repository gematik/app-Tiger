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
package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RbelFileWriterUtilsTest {

  @Test
  void readOldFile_pairingShouldBeCorrect() throws IOException {

    RbelLogger rbelLogger = RbelLogger.build(new RbelConfiguration());
    var rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());
    String tgrContent =
        FileUtils.readFileToString(new File("src/test/resources/rezepsFiltered.tgr"));
    rbelFileWriter.convertFromRbelFile(tgrContent, Optional.empty());

    var requests =
        rbelLogger.getRbelConverter().getMessageList().stream()
            .filter(msg -> msg.hasFacet(RbelRequestFacet.class))
            .toList();

    assertThat(requests).hasSize(48);

    requests.forEach(
        request -> {
          assertThat(request.hasFacet(TracingMessagePairFacet.class)).isTrue();
          assertThat(
                  request
                      .getFacetOrFail(TracingMessagePairFacet.class)
                      .getResponse()
                      .hasFacet(TracingMessagePairFacet.class))
              .isTrue();
        });
  }

  @Test
  void readFileTwice_shouldOnlyReadMsgsOnceBasedOnUuid() throws IOException {
    RbelLogger rbelLogger = RbelLogger.build(new RbelConfiguration());
    var rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());

    String rawSavedVauMessages =
        FileUtils.readFileToString(new File("src/test/resources/trafficLog.tgr"));
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages, Optional.empty());

    int initialNumberOfMessage = rbelLogger.getMessageHistory().size();
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages, Optional.empty());

    assertThat(rbelLogger.getMessageHistory()).hasSize(initialNumberOfMessage);
  }
}
