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

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Test class for test the pause button. */
class TPauseTests extends AbstractTests {
  @Test
  void testPauseButton() {
    page.querySelector("#test-tiger-logo").click();
    page.querySelector("#test-sidebar-pause-icon").click();
    assertAll(
        () -> assertThat(page.locator("#sidebar-left.test-sidebar-paused").isVisible()).isTrue()
        //  () -> assertThat(page.locator("#execution_table
        // .test-pending").first().isVisible()).isTrue()
        );

    screenshot(page, "sidebar_pause.png");
  }

  @AfterEach
  void unpause() {
    page.querySelector("#test-sidebar-pause-icon").click();
  }
}
