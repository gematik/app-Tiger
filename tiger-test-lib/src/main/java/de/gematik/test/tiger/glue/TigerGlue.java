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

package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Slf4j
public class TigerGlue {

    /**
     * Sets the given key to the given value in the global configuration store. Variable substitution is performed.
     *
     * @param key   key of the context
     * @param value value for the context entry with given key
     */
    @Wenn("TGR setze globale Variable {string} auf {string}")
    @When("TGR set global variable {string} to {string}")
    public void ctxtISetGlobalVariableTo(final String key, final String value) {
        TigerGlobalConfiguration.putValue(
            TigerGlobalConfiguration.resolvePlaceholders(key),
            TigerGlobalConfiguration.resolvePlaceholders(value));
    }

    /**
     * Sets the given key to the given value in the global configuration store. Variable substitution is performed. This
     * value will only be accessible from this exact thread.
     *
     * @param key   key of the context
     * @param value value for the context entry with given key
     */
    @Wenn("TGR setze lokale Variable {string} auf {string}")
    @When("TGR set local variable {string} to {string}")
    public void ctxtISetLocalVariableTo(final String key, final String value) {
        TigerGlobalConfiguration.putValue(
            TigerGlobalConfiguration.resolvePlaceholders(key),
            TigerGlobalConfiguration.resolvePlaceholders(value),
            SourceType.THREAD_CONTEXT);
    }

    /**
     * asserts that value with given key either equals or matches (regex) the given regex string. Variable substitution
     * is performed. This checks both global and local variables!
     * <p>
     *
     * @param key   key of entry to check
     * @param regex regular expression (or equals string) to compare the value of the entry to
     */
    @Dann("TGR prüfe Variable {string} stimmt überein mit {string}")
    @Then("TGR assert variable {string} matches {string}")
    public void ctxtAssertVariableMatches(final String key, final String regex) {
        final String resolvedKey = TigerGlobalConfiguration.resolvePlaceholders(key);
        String value = TigerGlobalConfiguration.readStringOptional(resolvedKey)
            .orElseThrow(() -> new TigerLibraryException(
                "Wanted to assert value of key " + key + " (resolved to " + resolvedKey + ") but couldn't find it!"));
        if (!Objects.equals(value, regex)) {
            assertThat(value).matches(regex);
        }
    }

    /**
     * asserts that value of context entry with given key either equals or matches (regex) the given regex string.
     * Variable substitution is performed.
     * <p>
     * Special values can be used:
     *
     * @param key key of entry to check
     */
    @Dann("TGR prüfe Variable {string} ist unbekannt")
    @Then("TGR assert variable {string} is unknown")
    public void ctxtAssertVariableUnknown(final String key) {
        final String resolvedKey = TigerGlobalConfiguration.resolvePlaceholders(key);
        final Optional<String> optionalValue = TigerGlobalConfiguration.readStringOptional(resolvedKey);
        assertThat(optionalValue)
            .withFailMessage("Wanted to assert value of key {} (resolved to {}) is not set "
                    + "but found value {}!",
                key, resolvedKey, optionalValue)
            .isEmpty();
    }

    @Gegebensei("TGR zeige {word} Banner {string}")
    @Given("TGR show {word} banner {string}")
    public void tgrShowColoredBanner(String color, String text) {
        log.info("\n" + Banner.toBannerStrWithCOLOR(TigerGlobalConfiguration.resolvePlaceholders(text), color.toUpperCase()));
    }

    @Gegebensei("TGR zeige {word} Text {string}")
    @Given("TGR show {word} text {string}")
    public void tgrShowColoredText(String color, String text) {
        log.info("\n" + Banner.toTextStr(TigerGlobalConfiguration.resolvePlaceholders(text), color.toUpperCase()));
    }

    @Gegebensei("TGR zeige Banner {string}")
    @Given("TGR show banner {string}")
    public void tgrIWantToShowBanner(String text) {
        log.info("\n" + Banner.toBannerStrWithCOLOR(TigerGlobalConfiguration.resolvePlaceholders(text), "WHITE"));
    }

    @When("TGR wait for user abort")
    @Wenn("TGR warte auf Abbruch")
    public void tgrWaitForUserAbort() {
        TigerDirector.waitForAcknowledgedQuit();
    }

    @When("TGR pause test run execution")
    @Wenn("TGR pausiere Testausführung")
    public void tgrPauseExecution() {
        TigerDirector.pauseExecution();
    }

    @When("TGR pause test run execution with message {string}")
    @Wenn("TGR pausiere Testausführung mit Nachricht {string}")
    public void tgrPauseExecutionWithMessage(String message) {
        TigerDirector.pauseExecution(TigerGlobalConfiguration.resolvePlaceholders(message));
    }

    @When("TGR pause test run execution with message {string} and message in case of error {string}")
    @Wenn("TGR pausiere Testausführung mit Nachricht {string} und Meldung im Fehlerfall {string}")
    public void tgrPauseExecutionWithMessageAndErrorMessage(String message, String errorMessage) {
        TigerDirector.pauseExecutionAndFailIfDesired(TigerGlobalConfiguration.resolvePlaceholders(message),
            TigerGlobalConfiguration.resolvePlaceholders(errorMessage));
    }

    @When("TGR show HTML Notification:")
    @Wenn("TGR zeige HTML Notification:")
    public void tgrShowHtmlNotification(String message) {
        // TODO merge this with TigerDirector.pauseExecution
        final String bannerMessage = TigerGlobalConfiguration.resolvePlaceholders(message);
        if (TigerDirector.getLibConfig().isActivateWorkflowUi()) {
            TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(TigerStatusUpdate.builder()
                .bannerMessage(bannerMessage)
                .bannerColor("green")
                .bannerType(BannerType.STEP_WAIT)
                .bannerIsHtml(true)
                .build());
            await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(TigerDirector.getLibConfig().getPauseExecutionTimeoutSeconds(), TimeUnit.SECONDS)
                .until(() -> TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedOnWorkflowUi());
            TigerDirector.getTigerTestEnvMgr().resetConfirmationFromWorkflowUi();
        } else {
            log.warn("Workflow UI is not active! Can't display message '{}'", bannerMessage);
        }
    }

    @When("TGR assert {string} matches {string}")
    @Dann("TGR prüfe das {string} mit {string} überein stimmt")
    public void tgrAssertMatches(String rawValue1, String rawValue2) {
        String value1 = TigerGlobalConfiguration.resolvePlaceholders(rawValue1);
        String value2 = TigerGlobalConfiguration.resolvePlaceholders(rawValue2);
        if (!Objects.equals(value1, value2)) {
            assertThat(value1).matches(value2);
        }
    }
}
