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

package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RbelFileWriterUtilsTest {

  @Test
  void readFileTwice_shouldOnlyReadMsgsOnceBasedOnUuid() throws IOException {
    RbelLogger rbelLogger = RbelLogger.build(new RbelConfiguration().setActivateAsn1Parsing(false));
    var rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());

    String rawSavedVauMessages =
        FileUtils.readFileToString(new File("src/test/resources/trafficLog.tgr"));
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages);

    int initialNumberOfMessage = rbelLogger.getMessageHistory().size();
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages);

    assertThat(rbelLogger.getMessageHistory().size()).isEqualTo(initialNumberOfMessage);
  }
}
