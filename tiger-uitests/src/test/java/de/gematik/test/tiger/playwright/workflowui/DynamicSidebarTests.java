/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import lombok.extern.slf4j.Slf4j;


@Slf4j
class DynamicSidebarTests extends AbstractTests {

//    @ParameterizedTest
//    @ValueSource(strings = {"#test-tiger-logo",
//                            "#test-sidebar-status-icon",
//                            "#test-sidebar-feature-icon",
//                            "#test-sidebar-server-icon"})
//    void testSidebarIsClosedAndOpensOnIconClickAndClosesAgain(String iconSelector) {
//        page.querySelector(iconSelector).click();
//        assertThat(page.querySelector("#test-sidebar-title").isVisible()).isTrue();
//        page.querySelector(iconSelector).click();
//        assertThat(page.querySelector("#test-sidebar-title").isVisible()).isFalse();
//    }
//
//    @Test
//    void testPauseButton() {
//        page.querySelector("#test-tiger-logo").click();
//        page.querySelector("#test-sidebar-pause-icon").click();
//        assertAll(
//            () -> assertThat(page.locator("#sidebar-left.test-sidebar-paused").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#execution_table .test-status-pending").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#execution_table .test-pending").isVisible()).isTrue()
//        );
//    }
//
//
////    @Test
////    @Ignore
////    void testQuitButton() {
////        page.querySelector("#test-tiger-logo").click();
////        page.querySelector("#test-sidebar-quit-icon").click();
////        assertAll(
////            () -> assertThat(page.querySelector("#sidebar-left.test-sidebar-quit").isVisible()).isTrue(),
////            () -> assertThat(page.querySelector("#workflow-messages .test-messages-quit").isVisible()).isTrue()
//@BeforeEach
//void setPage() {
//    page = browser.newPage();
//    page.navigate("http://localhost:" + port);
//}
////        );
////    }
//
//    @Test
//    void testSidebarIsClosedWhenClickedOnDoubleArrow() {
//        page.querySelector("#test-tiger-logo").click();
//        page.querySelector("#test-sidebar-close-icon").click();
//        assertThat(page.querySelector("#test-sidebar-title").isVisible()).isFalse();
//    }
//
//    @Test
//    void testFeatureBoxClickOnLastStep() {
//        page.querySelector("#test-tiger-logo").click();
//        page.locator(".test-sidebar-scenario-name").last().click();
//        assertThat(page.querySelector("#test-sidebar-title").isHidden()).isTrue();
//    }
//
//    @Test
//    void testPassedStepInFeatureBoxAndInExecutionPane() {
//        page.querySelector("#test-tiger-logo").click();
//        assertAll(
//            () -> assertThat(page.locator("#sidebar-left .test-passed").first().isVisible()).isTrue(),
//            () -> assertThat(page.locator("#execution_table .test-passed").first().isVisible()).isTrue()
//        );
//    }
//
//    @Test
//    void testFindFailedStepInFeatureBoxAndInExecutionPane() {
//        page.querySelector("#test-tiger-logo").click();
//        assertAll(
//            () -> assertThat(page.locator("#sidebar-left .test-failed").first().isVisible()).isTrue(),
//            () -> assertThat(page.locator("#execution_table .test-failed").first().isVisible()).isTrue()
//        );
//    }
//
//    @Test
//    void ServerBoxAllServerRunning() {
//        page.querySelector("#test-tiger-logo").click();
//        List<Locator> servers = page.locator("#test-sidebar-server-status-box .test-sidebar-server-status").all();
//        servers.forEach(server -> {
//            assertThat(server.textContent()).contains("RUNNING");
//        });
//    }
//    @ParameterizedTest
//    @ValueSource(ints = {0, 2})
//    void ServerBoxTigerProxyWebUiStarted(int counter) throws InterruptedException {
//        page.querySelector("#test-tiger-logo").click();
//        Page page1 = page.waitForPopup(() -> {
//            page.locator("#sidebar-left .test-sidebar-server-url-icon").nth(counter).click();
//        });
//        // somehow I need to wait for the remoteTigerProxy popup to be loaded
//        Thread.sleep(1000);
//        assertThat(page1.locator("#test-tiger-logo").isVisible()).isTrue();
//    }
//
//    @ParameterizedTest
//    @ValueSource(ints = {0, 1, 2})
//    void ServerBoxLocalTigerProxyLogfiles(int counter) {
//        page.querySelector("#test-tiger-logo").click();
//        page.locator("#sidebar-left .test-sidebar-server-log-icon").nth(counter).click();
//        assertAll(
//            () -> assertThat(page.locator(".test-sidebar-server-logs").nth(counter).isVisible()).isTrue(),
//            () -> assertThat(page.locator(".test-sidebar-server-logs").nth(counter).locator(".test-sidebar-server-log").first().isVisible()).isTrue(),
//            () -> assertThat(page.locator(".test-sidebar-server-logs").nth(counter).locator(".test-sidebar-server-log").last().isVisible()).isTrue()
//        );
//    }

}
