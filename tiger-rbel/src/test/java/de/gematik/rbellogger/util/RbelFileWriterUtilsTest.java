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
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelFileWriterUtilsTest {
  private RbelLogger rbelLogger;
  private RbelFileWriter rbelFileWriter;

  @BeforeEach
  void setUp() {
    rbelLogger = RbelLogger.build(new RbelConfiguration());
    rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());
  }

  @Test
  void readOldFile_pairingShouldBeCorrect() throws IOException {

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

    String rawSavedVauMessages =
        FileUtils.readFileToString(new File("src/test/resources/trafficLog.tgr"));
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages, Optional.empty());

    int initialNumberOfMessage = rbelLogger.getMessageHistory().size();
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages, Optional.empty());

    assertThat(rbelLogger.getMessageHistory()).hasSize(initialNumberOfMessage);
  }

  @Test
  void writeVersionHeader_shouldBeLazilyGeneratedOnFirstWrite() {

    var testElement = RbelElement.builder().content(RbelContent.of("TEST".getBytes())).build();

    String firstWrite = rbelFileWriter.convertToRbelFileString(testElement);
    String secondWrite = rbelFileWriter.convertToRbelFileString(testElement);

    assertThat(firstWrite).contains("tigerVersion");

    assertThat(secondWrite).doesNotContain("\"tigerVersion\"");
  }

  @Test
  void writeVersionHeader_shouldContainActualTigerVersion() {

    var testElement = RbelElement.builder().content(RbelContent.of("TEST".getBytes())).build();

    String output = rbelFileWriter.convertToRbelFileString(testElement);

    String expectedVersion =
        de.gematik.test.tiger.common.util.TigerVersionProvider.getTigerVersionString();
    assertThat(output).contains("\"tigerVersion\":\"" + expectedVersion + "\"");
  }

  @Test
  void readVersionHeader_shouldExtractVersion() {

    String fileContent =
        """
        {"uuid":"test-uuid","rawMessageContent":"VEVTVA==","sequenceNumber":1,"tigerVersion":"4.1.12-test"}
        """;

    var messages = rbelFileWriter.convertFromRbelFile(fileContent, Optional.empty());

    assertThat(messages).hasSize(1);
    assertThat(rbelFileWriter.getLastReadTigerVersion()).isPresent();
    assertThat(rbelFileWriter.getLastReadTigerVersion().get()).isEqualTo("4.1.12-test");
  }

  @Test
  void disableVersionHeader_shouldNotWriteVersion() {
    rbelFileWriter.setWriteVersionHeader(false);

    var testElement = RbelElement.builder().content(RbelContent.of("TEST".getBytes())).build();

    String firstWrite = rbelFileWriter.convertToRbelFileString(testElement);
    String secondWrite = rbelFileWriter.convertToRbelFileString(testElement);

    assertThat(firstWrite).doesNotContain("tigerVersion");
    assertThat(secondWrite).doesNotContain("tigerVersion");
  }

  @Test
  void writeVersionHeader_shouldBeEmbeddedIntoFirstEntryOnly() {

    var testElement = RbelElement.builder().content(RbelContent.of("TEST".getBytes())).build();

    String firstWrite = rbelFileWriter.convertToRbelFileString(testElement);
    String secondWrite = rbelFileWriter.convertToRbelFileString(testElement);

    assertThat(firstWrite).contains("\"tigerVersion\"");
    assertThat(secondWrite).doesNotContain("\"tigerVersion\"");
  }

  @Test
  void readVersionHeader_shouldHandleEmbeddedVersionInFirstEntry() {

    String fileContent =
        """
        {"uuid":"msg1","rawMessageContent":"VEVTVA==","sequenceNumber":1,"tigerVersion":"4.1.12-test"}
        {"uuid":"test-uuid","rawMessageContent":"VEVTVA==","sequenceNumber":2}
        """;

    var messages = rbelFileWriter.convertFromRbelFile(fileContent, Optional.empty());

    assertThat(messages).hasSize(2);
    assertThat(rbelFileWriter.getLastReadTigerVersion()).contains("4.1.12-test");
  }

  @Test
  void readFileWithVersion_shouldAllowQueryingVersionAfterwards() {

    assertThat(rbelFileWriter.getLastReadTigerVersion()).isEmpty();

    String fileContent =
        """
        {"uuid":"msg1","rawMessageContent":"VEVTVA==","sequenceNumber":1,"tigerVersion":"3.5.7-20250101"}
        {"uuid":"msg2","rawMessageContent":"VEVTVA==","sequenceNumber":2}
        """;

    var messages = rbelFileWriter.convertFromRbelFile(fileContent, Optional.empty());

    assertThat(messages).hasSize(2);
    assertThat(rbelFileWriter.getLastReadTigerVersion()).contains("3.5.7-20250101");
  }

  @Test
  void readFileWithoutVersion_shouldStillParseMessages() {

    String fileContent =
        """
        {"uuid":"msg1","rawMessageContent":"VEVTVA==","sequenceNumber":1}
        {"uuid":"msg2","rawMessageContent":"VEVTVA==","sequenceNumber":2}
        """;

    var messages = rbelFileWriter.convertFromRbelFile(fileContent, Optional.empty());

    assertThat(messages).hasSize(2);
    assertThat(rbelFileWriter.getLastReadTigerVersion()).isEmpty();
  }

  @Test
  void messageWithBothTigerVersionAndContent_shouldBeProcessedAsRegularMessage() {

    String fileContent =
        """
        {"uuid":"msg1","rawMessageContent":"VEVTVA==","tigerVersion":"2.1.9","sequenceNumber":1}
        """;

    var messages = rbelFileWriter.convertFromRbelFile(fileContent, Optional.empty());

    assertThat(messages).hasSize(1);

    assertThat(rbelFileWriter.getLastReadTigerVersion()).get().isEqualTo("2.1.9");
  }

  @Test
  void multipleVersionHeaders_shouldKeepLastVersion() {

    String fileContent =
        """
        {"uuid":"msg1","rawMessageContent":"VEVTVA==","sequenceNumber":1,"tigerVersion":"3.0.0-initial"}
        {"uuid":"msg2","rawMessageContent":"VEVTVA==","sequenceNumber":2,"tigerVersion":"3.1.0-append1"}
        {"uuid":"msg3","rawMessageContent":"VEVTVA==","sequenceNumber":3,"tigerVersion":"3.2.0-append2"}
        """;

    var messages = rbelFileWriter.convertFromRbelFile(fileContent, Optional.empty());

    assertThat(messages).hasSize(3);

    assertThat(rbelFileWriter.getLastReadTigerVersion()).isPresent();
    assertThat(rbelFileWriter.getLastReadTigerVersion().get()).isEqualTo("3.2.0-append2");
  }
}
