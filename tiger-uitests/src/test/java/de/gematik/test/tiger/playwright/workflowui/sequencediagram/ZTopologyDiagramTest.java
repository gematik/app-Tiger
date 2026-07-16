/*
 * Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.playwright.workflowui.sequencediagram;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZTopologyDiagramTest extends AbstractBase {

  @Test
  @Order(1)
  void testScreenshotTopologyDiagramWithLiveEdges() {

    switchToTopologyDiagram();
    PlaywrightAssertions.assertThat(page.locator(".tiger-edge-dynamicTraffic").first()).isVisible();
    screenshotDiagram("topology_live_edges.png");
  }

  @Test
  @Order(2)
  void testScreenshotFilterButtons() {
    switchToTopologyDiagram();
    var edgeToggleControls = page.locator(".edge-toggle-controls");
    PlaywrightAssertions.assertThat(edgeToggleControls).isVisible();
    screenshotElement(edgeToggleControls, "topology_filter_buttons.png");
  }

  @Test
  @Order(3)
  void testScreenshotRemoteProxy() {}

  void switchToTopologyDiagram() {
    var topologyDiagramTabButton = page.locator("#test-topology-tab");
    topologyDiagramTabButton.click();
  }

  private void fitDiagramToView() {
    page.locator(".vue-flow__controls-fitview").click();
  }

  private static final String HIDE_OVERLAYS_CSS =
      ".diagram-legend-panel, .vue-flow__minimap, .vue-flow__controls, .vue-flow__background { display: none !important; }";

  protected void screenshotDiagram(String outputFileName) {
    fitDiagramToView();
    page.waitForTimeout(500);
    page.locator(".vue-flow")
        .screenshot(
            new Locator.ScreenshotOptions()
                .setStyle(HIDE_OVERLAYS_CSS)
                .setPath(getPath(outputFileName)));
  }
}
