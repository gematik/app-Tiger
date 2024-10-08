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

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.test.tiger.lib.rbel.RbelMessageValidator.RBEL_REQUEST_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.data.pop3.RbelPop3Command;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("java:S2699")
public class RbelMessageValidatorFindMessageStepsTest extends AbstractRbelMessageValidatorTest {

  @BeforeEach
  public void setUp() {
    if (rbelMessageValidator == null) {
      super.setUp();
      readPop3Messages();
    } else {
      rbelMessageValidator.clearCurrentMessages();
    }
  }

  private void readPop3Messages() {
    readTgrFileAndStoreForRbelMessageValidator(
        "src/test/resources/testdata/pop3.tgr", List.of("pop3", "mime"));
  }

  @Test
  void testFindMessageWithNodeMatching_findResponse() {
    glue.findMessageWithNodeMatching("$.pop3Body.mimeBody", null);
    glue.currentResponseMessageContainsNode("$.pop3Body.mimeBody");
    glue.currentRequestMessageAttributeMatches("$.pop3Command", RbelPop3Command.RETR.name());
  }

  @Test
  void testFindMessageWithNodeMatching_findLastResponseViaHeader() {
    glue.findLastMessageWithNodeMatching("$.pop3Header", ".*noop.*");
    glue.currentResponseMessageContainsNode("$.pop3Header");
    glue.currentRequestMessageAttributeMatches("$.pop3Command", RbelPop3Command.NOOP.name());
  }

  @Test
  void testFindMessageWithNodeMatching_findNextRequest() {
    glue.findNextMessageWithNodeMatching("$.pop3Command", RbelPop3Command.QUIT.name());
    glue.currentRequestMessageContainsNode("$.pop3Command");
    glue.currentResponseMessageAttributeMatches("$.pop3Header", ".*bye.*see you.*");
  }

  @Test
  void testFindMessageWithNodeMatching_findRequest() {
    glue.findMessageWithNodeMatching("$.pop3Command", RbelPop3Command.RETR.name());
    glue.currentResponseMessageAttributeMatches("$.pop3Status", "+OK");
    glue.currentResponseMessageAttributeMatches("$.pop3Body.mimeHeader.subject", ".*test.*");
  }

  @Test
  void testFindMessageWithNodeMatching_missingMessageThrows() {
    assertThatThrownBy(() -> glue.findNextMessageWithNodeMatching("$.pop3Status", "-ERR"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFindMessageWithNodeMatching_findLastResponse() {
    glue.findLastMessageWithNodeMatching("$.pop3Status", null);
    glue.currentRequestMessageAttributeMatches("$.pop3Command", RbelPop3Command.QUIT.name());
  }

  @Test
  void testFindMessageWithNodeMatching_findFirstResponse() {
    glue.findMessageWithNodeMatching("$.pop3Status", "+OK");
    assertThatThrownBy(() -> glue.currentRequestMessageContainsNode("$.pop3Command"))
        .isInstanceOf(AssertionError.class);
    glue.currentResponseMessageAttributeMatches("$.pop3Status", "+OK");
    // response is also set to currentRequest because first response has no request
    glue.currentRequestMessageAttributeMatches("$.pop3Status", "+OK");
  }

  @Test
  void testFindMessageWithNodeMatching_findNoNextButFindLastResponse() {
    final String capaName = RbelPop3Command.CAPA.name();

    glue.findMessageWithNodeMatching("$.pop3Status", "+OK");

    glue.findNextMessageWithNodeMatching("$.pop3Status", null);
    glue.currentRequestMessageAttributeMatches("$.pop3Command", capaName);

    assertThatThrownBy(() -> glue.findNextMessageWithNodeMatching("$.pop3Command", capaName))
        .isInstanceOf(AssertionError.class);

    glue.findLastMessageWithNodeMatching("$.pop3Command", capaName);
  }

  @Test
  void testFindMessageWithNode_findResponseWithMimeBody() {
    glue.findMessageWithNode("$.pop3Body.mimeBody");
    glue.currentResponseMessageContainsNode("$.pop3Body.mimeBody");
    glue.currentRequestMessageAttributeMatches("$.pop3Command", RbelPop3Command.RETR.name());

    testFindMessageWithNode_findFirstResponseHasNoRequest();

    glue.findLastMessageWithNode("$.pop3Command");
    glue.currentRequestMessageAttributeMatches("$.pop3Command", RbelPop3Command.QUIT.name());

    assertThatThrownBy(() -> glue.findNextMessageWithNode("$.pop3Command"))
        .isInstanceOf(AssertionError.class);

    glue.findLastMessageWithNode("$.pop3Command");
  }

  private void testFindMessageWithNode_findFirstResponseHasNoRequest() {
    glue.findMessageWithNode("$.pop3Status");
    assertThatThrownBy(() -> glue.currentRequestMessageContainsNode("$.pop3Command"))
        .isInstanceOf(AssertionError.class);
    glue.currentResponseMessageAttributeMatches("$.pop3Status", "+OK");
    // response is also set to currentRequest because first response has no request
    glue.currentRequestMessageAttributeMatches("$.pop3Status", "+OK");
  }

  @Test
  void testFindMessageWithNode_findFirstResponseAfterFindLastResponse() {
    glue.findLastMessageWithNode("$.pop3Status");
    glue.currentRequestMessageAttributeMatches("$.pop3Command", RbelPop3Command.QUIT.name());

    glue.findMessageWithNode("$.pop3Status");
  }

  @Test
  void testFindMessageWithNode_findFirstResponse() {
    glue.findMessageWithNode("$.pop3Body.mimeBody");
    glue.currentResponseMessageAttributeMatches("$.pop3Status", "+OK");
    glue.currentResponseMessageAttributeMatches("$.pop3Body.mimeHeader.subject", ".*test.*");
  }

  @Test
  void testFindMessageWithNode_findLastRequest() {
    glue.findLastMessageWithNode("$.pop3Command");
    glue.currentRequestMessageContainsNode("$.pop3Command");
    glue.currentResponseMessageAttributeMatches("$.pop3Header", ".*bye.*see you.*");
  }

  @Test
  void testFindMessageWithNode_findNoNextAfterLastResponse() {
    RBEL_REQUEST_TIMEOUT.putValue(1); // don't wait so long before giving up
    try {
      glue.findLastMessageWithNode("$.pop3Body.mimeBody");
      glue.currentRequestMessageContainsNode("$.pop3Arguments");
      glue.currentRequestMessageAttributeMatches("$.pop3Command", RbelPop3Command.RETR.name());

      assertThatThrownBy(() -> glue.findNextMessageWithNode("$.pop3Body.mimeBody"))
          .isInstanceOf(AssertionError.class);
    } finally {
      RBEL_REQUEST_TIMEOUT.clearValue();
    }
  }
}
