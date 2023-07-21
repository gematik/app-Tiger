/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import org.junit.Ignore;

@Ignore
class StaticMainContentTests extends AbstractTests  {

//    @Test
//    void testExecutionPaneActive() {
//        assertAll(
//            () -> assertThat(page.locator("#test-execution-pane-tab.active").isVisible()).isTrue(),
//            () -> assertThat(page.locator("#test-server-log-tab.active").isVisible()).isFalse()
//        );
//    }
//
//    @Test
//    void testExecutionPaneStatus() {
//        assertThat(page.locator("#test-execution-pane-date").isVisible()).isTrue();
//    }
//
//    @Test
//    void testExecutionPaneGematikLogo() {
//        assertThat(page.locator("#test-gematik-logo").isVisible()).isTrue();
//    }
//
//    @Test
//    void testExecutionPaneScenariosExists() {
//        assertAll(
//            () -> assertThat(page.locator(".test-execution-pane-feature-title").count()).isEqualTo(1),
//            () -> assertThat(page.locator(".test-execution-pane-scenario-title").count()).isEqualTo(25),
//            () -> assertThat(
//                page.locator(".test-execution-pane-feature-title").locator(".test-failed").count()).isEqualTo(1),
//            () -> assertThat(
//                page.locator(".test-execution-pane-scenario-title").locator(".test-failed").count()).isEqualTo(1)
//        );
//    }
//
//    @Test
//    void testServerPanExists() {
//        page.querySelector("#test-server-log-tab").click();
//        assertThat(page.locator("#test-server-log-pane-buttons").locator(".active").count()).isEqualTo(1);
//        assertThat(page.locator("#test-server-log-pane-buttons").locator(".test-server-log-pane-server-all.active")
//            .isVisible()).isTrue();
//    }
//
//    @ParameterizedTest
//    @ValueSource(ints = {0, 1, 2})
//    void testServerPanServerButtonsExist(int counter) {
//        page.querySelector("#test-server-log-tab").click();
//        assertThat(
//            page.locator("#test-server-log-pane-buttons").locator(".test-server-log-pane-server-" + counter)
//                .isVisible()).isFalse();
//    }
//
//    @Test
//    void testServerPanInputTextIsEmpty() {
//        page.querySelector("#test-server-log-tab").click();
//        assertThat(page.locator("#test-server-log-pane-input-text").isVisible()).isTrue();
//        assertThat(page.locator("#test-server-log-pane-input-text").textContent()).isNullOrEmpty();
//    }
//
//    @Test
//    void testServerPanLogLevelIsALL() {
//        page.querySelector("#test-server-log-tab").click();
//        assertThat(page.locator("#test-server-log-pane-select").isVisible()).isTrue();
//        assertThat(page.locator("#test-server-log-pane-select").inputValue()).isEqualTo(page.locator("#test-server-log-pane-select-0").getAttribute("value"));
//    }
//
//    @ParameterizedTest
//    @ValueSource(ints = {1, 2, 3})
//    void testServerPanLogsAreAvailable(int col) {
//        page.querySelector("#test-server-log-tab").click();
//        assertThat(page.locator("#logs_pane .test-server-log-pane-log-row").count()).isPositive();
//        assertThat(page.locator("#logs_pane .test-server-log-pane-log-col-"+ col).isVisible()).isTrue();
//        assertThat(page.locator("#logs_pane .test-server-log-pane-log-col-"+ col).count()).isPositive();
//    }


}