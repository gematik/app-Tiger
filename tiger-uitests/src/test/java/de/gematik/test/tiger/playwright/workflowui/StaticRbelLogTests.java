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

package de.gematik.test.tiger.playwright.workflowui;

import org.junit.Ignore;

@Ignore
class StaticRbelLogTests extends AbstractTests  {

//    @Test
//    void testExecutionPaneRbelLogo() {
//        page.querySelector("#test-execution-pane-tab").click();
//        page.locator("#test-webui-slider").click();
//        assertThat(page.locator("#test-rbel-logo").isVisible()).isTrue();
//    }
//
//    @Test
//    void testExecutionPaneRbelWebUiURLExists() {
//        page.querySelector("#test-execution-pane-tab").click();
//        page.locator("#test-webui-slider").click();
//        assertThat(page.locator("#test-rbel-webui-url").isVisible()).isTrue();
//    }
//
//    @Test
//    void testNavbarWithButtonsExists() {
//        page.querySelector("#test-execution-pane-tab").click();
//        page.locator("#test-webui-slider").click();
//        assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
//        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar").isVisible()).isTrue();
//        int countAllNavItems = page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar .test-navbar-item").count();
//        int countNotEmbedNavItems = page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar .test-webui-navbar-item-notembedded").count();
//        assertThat(countAllNavItems - countNotEmbedNavItems).isEqualTo(5);
//    }
//    @Test
//    void testRbelMessagesExists() {
//        page.querySelector("#test-execution-pane-tab").click();
//        page.locator("#test-webui-slider").click();
//        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card").count()).isPositive();
//        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-header").count()).isPositive();
//        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-content").count()).isPositive();
//    }
}
