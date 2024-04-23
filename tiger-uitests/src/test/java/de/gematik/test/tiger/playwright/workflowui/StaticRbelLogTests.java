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

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

/** Tests for static content of the web ui content, e.g. rbel logo. */
class StaticRbelLogTests extends AbstractTests {

  @Test
  void testExecutionPaneRbelLogo() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    await().untilAsserted(() -> assertThat(page.locator("#test-rbel-logo").isVisible()).isTrue());
    screenshot(page, "maincontent_rbelpath.png");
    screenshotElementById(
        page, "maincontent_rbelpath_urllink_highlight.png", "test-rbel-webui-url");
  }
}
