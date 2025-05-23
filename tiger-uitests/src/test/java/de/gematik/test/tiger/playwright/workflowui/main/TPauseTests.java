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
package de.gematik.test.tiger.playwright.workflowui.main;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Test class for test the pause button. */
class TPauseTests extends AbstractBase {
  @Test
  void testPauseButton() {
    openSidebar();
    page.querySelector("#test-sidebar-pause-icon").click();
    assertAll(
        () -> assertThat(page.locator("#sidebar-left.test-sidebar-paused")).isVisible()
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
