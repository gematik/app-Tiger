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

import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;


@Slf4j
@Ignore
class StaticSidebarTests extends AbstractTests {

//    @Test
//    void testSidebarClosedIconsAreVisible() {
//        assertAll(
//            () -> assertThat(page.querySelector("#test-sidebar-title").isVisible()).isFalse(),
//            () -> assertThat(page.querySelector("div.test-sidebar-collapsed").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-tiger-logo").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-quit-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-pause-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-status-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-feature-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-server-icon").isVisible()).isTrue()
//        );
//    }
//
//    @Test
//    void testSidebarOpenIconsAreVisible() {
//        page.querySelector("#test-tiger-logo").click();
//        assertAll(
//            () -> assertThat(page.querySelector("#test-sidebar-title").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-status").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-feature").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-server").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-version").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-build").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-tiger-logo").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-quit-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-pause-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-status-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-feature-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-server-icon").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-version").isVisible()).isTrue(),
//            () -> assertThat(page.querySelector("#test-sidebar-build").isVisible()).isTrue()
//        );
//    }
//
//    @Test
//    void testStatus() {
//        page.querySelector("#test-tiger-logo").click();
//        assertAll(
//            () -> assertThat(page.locator("#test-sidebar-statusbox").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#test-sidebar-statusbox .test-sidebar-status-features").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#test-sidebar-statusbox .test-sidebar-status-scenarios").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#test-sidebar-status-started").isVisible()).isTrue()
//        );
//    }
//
//    @Test
//    void testFeatures() {
//        page.querySelector("#test-tiger-logo").click();
//        assertAll(
//            () -> assertThat(page.locator("#test-sidebar-featurelistbox").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name").count()).isEqualTo(1),
//            () -> assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name").count()).isEqualTo(25),
//            () -> assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-index").count()).isEqualTo(11)
//        );
//    }
//
//    @Test
//    void testServers() {
//        page.querySelector("#test-tiger-logo").click();
//        assertAll(
//            () -> assertThat(page.locator("#test-sidebar-server-status-box").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-serverbox").count()).isEqualTo(3),
//            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-name").count()).isEqualTo(3),
//            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-status").count()).isEqualTo(3),
//            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-url").count()).isEqualTo(3),
//            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-url-icon").count()).isEqualTo(3),
//            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-log-icon").count()).isEqualTo(3)
//        );
//    }
}
