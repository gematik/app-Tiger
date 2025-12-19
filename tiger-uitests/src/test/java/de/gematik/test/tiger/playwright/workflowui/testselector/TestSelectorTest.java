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
package de.gematik.test.tiger.playwright.workflowui.testselector;

import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Clip;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class TestSelectorTest extends AbstractBase {

  /**
   * The table shows a tree representation of the tests cases including the folders where the
   * feature files are located. See the folder src/test/resources/features/testselector to check
   * which feature files are loaded in the test
   *
   * <p>- this is a feature
   *
   * <p>- this a scenario in the feature
   *
   * <p>- This is a scenario outline
   *
   * <p>- This is another scenario outline - example 1
   *
   * <p>- This is another scenario outline - example 2
   *
   * <p>- This is another scenario outline - example 3
   *
   * <p>- This is another scenario outline - example 4
   *
   * <p>- This is another scenario outline - example 5
   *
   * <p>- This is another scenario outline - example 6
   *
   * <p>- subfolderA
   *
   * <p>- feature A
   *
   * <p>- scenario in feature A
   *
   * <p>- subfolderAB
   *
   * <p>- feature AB
   *
   * <p>- scenario in feature AB
   *
   * <p>- subfolderB
   *
   * <p>- feature B
   *
   * <p>- scenario in feature B
   */
  private static final int NUMBER_OF_ROWS_COLLAPSED = 3;

  private static final int NUMBER_OF_ROWS_EXPANDED = 18;

  private Locator selectorModal;
  private Locator tableRows;

  void waitForModal() {
    log.info("Waiting for test selector modal...");
    openSidebar();
    var openModalButton = page.locator("#open-test-selector-button");
    openModalButton.click();
    page.pause();
    selectorModal = page.locator("#testselector-table");
    selectorModal.waitFor();
    tableRows = selectorModal.locator("tbody tr");
    // Waiting for the rows to be fulled takes a bit longer, because
    // the tests need to be discovered and sent to the frontend
    await().atMost(20, TimeUnit.SECONDS).until(() -> tableRows.count() == NUMBER_OF_ROWS_COLLAPSED);
    selectorModal.getByText("Expand All").click();
    await().atMost(20, TimeUnit.SECONDS).until(() -> tableRows.count() == NUMBER_OF_ROWS_EXPANDED);
    log.info("stopped waiting for test selector modal...");
  }

  @Override
  protected void setBackToNormalState() {
    if (selectorModal.isVisible()) {
      page.locator(".p-dialog-header .p-dialog-close-button").click();
    }
    openSidebar();
    page.locator("#open-test-selector-button").click();
  }

  @BeforeEach
  public void setup() {
    waitForModal();
  }

  @Test
  void takeScreenshots() {
    screenshotTestSelectorModal();
    screenshotTagsArea();
    screenshotOpenTagDropdownMenu();
    screenshotSearchFields();
    screenshotSaveLoadButtons();
    screenshotRunButton();
  }

  private void screenshotRunButton() {
    screenshotOnlyElementByClassname(page, "testselector_run_button.png", "dialog-header--sticky");
  }

  private void screenshotSaveLoadButtons() {
    screenshotOnlyElementById(page, "testselector_save_load.png", "testselector-action-buttons");
  }

  private void screenshotSearchFields() {
    screenshotElement(selectorModal.locator("thead"), "testselector_searchfields.png");
  }

  private void screenshotOpenTagDropdownMenu() {
    var splitButton =
        selectorModal.locator(".tag-selection-button").first().locator(".p-splitbutton-dropdown");
    splitButton.click();
    screenshotButtonWithTooltip(
        page,
        selectorModal.locator(".tag-selection-button").first(),
        page.locator(".p-tieredmenu.p-component.p-tieredmenu-overlay"),
        "testselector_tags_dropdown.png");
    splitButton.click();
  }

  private void screenshotTagsArea() {
    screenshotOnlyElementById(page, "testselector_tags.png", "testselector-tags");
  }

  private void screenshotTestSelectorModal() {
    screenshotOnlyElementByClassname(page, "testselector_modal.png", "p-dialog");
  }

  public void screenshotButtonWithTooltip(
      Page page, Locator button, Locator tooltip, String fileName) {
    // Ensure both are visible and positioned
    button.scrollIntoViewIfNeeded();
    tooltip.waitFor();

    // Get bounding boxes
    var btnBox = button.boundingBox();
    var tipBox = tooltip.boundingBox();

    if (btnBox == null || tipBox == null) {
      throw new RuntimeException("Could not resolve bounding boxes for button or tooltip.");
    }

    // Union the two rectangles
    double x = Math.min(btnBox.x, tipBox.x);
    double y = Math.min(btnBox.y, tipBox.y);
    double right = Math.max(btnBox.x + btnBox.width, tipBox.x + tipBox.width);
    double bottom = Math.max(btnBox.y + btnBox.height, tipBox.y + tipBox.height);
    double width = right - x;
    double height = bottom - y;

    // Take clipped screenshot of the page
    page.screenshot(
        new Page.ScreenshotOptions()
            .setClip(new Clip(x, y, width, height))
            .setPath(getPath(fileName)));
  }
}
