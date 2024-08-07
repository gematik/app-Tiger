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

package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TigerGlue {

  /**
   * Sets the given key to the given value in the global configuration store. Variable substitution
   * is performed.
   *
   * @param key key of the context
   * @param value value for the context entry with given key
   */
  @Wenn("TGR setze globale Variable {tigerResolvedString} auf {tigerResolvedString}")
  @When("TGR set global variable {tigerResolvedString} to {tigerResolvedString}")
  public void ctxtISetGlobalVariableTo(final String key, final String value) {
    log.debug("Setting global variable {} to '{}'", key, value);
    TigerGlobalConfiguration.putValue(key, value);
  }

  /**
   * Sets the given key to the given value in the global configuration store. Variable substitution
   * is performed. This value will only be available in the given scenario being clear up after the
   * scenario run is finished.
   *
   * @param key key of the context
   * @param value value for the context entry with given key
   */
  @Wenn("TGR setze lokale Variable {tigerResolvedString} auf {tigerResolvedString}")
  @When("TGR set local variable {tigerResolvedString} to {tigerResolvedString}")
  public void ctxtISetLocalVariableTo(final String key, final String value) {
    log.debug("Setting local variable {} to '{}'", key, value);
    TigerGlobalConfiguration.putValue(key, value, SourceType.LOCAL_TEST_CASE_CONTEXT);
  }

  @Wenn("TGR setze lokale Feature Variable {tigerResolvedString} auf {tigerResolvedString}")
  @When("TGR set local feature variable {tigerResolvedString} to {tigerResolvedString}")
  public void setFeatureVariable(final String key, final String value) {
    log.debug("Setting feature variable {} to '{}'", key, value);
    TigerGlobalConfiguration.putValue(key, value, SourceType.TEST_CONTEXT);
  }

  /**
   * asserts that value with given key either equals or matches (regex) the given regex string.
   * Variable substitution is performed. This checks both global and local variables!
   *
   * <p>
   *
   * @param key key of entry to check
   * @param regex regular expression (or equals string) to compare the value of the entry to
   */
  @Dann("TGR prüfe Variable {tigerResolvedString} stimmt überein mit {tigerResolvedString}")
  @Then("TGR assert variable {tigerResolvedString} matches {tigerResolvedString}")
  public void ctxtAssertVariableMatches(final String key, final String regex) {
    String value =
        TigerGlobalConfiguration.readStringOptional(key)
            .orElseThrow(
                () ->
                    new TigerLibraryException(
                        "Wanted to assert value of key "
                            + key
                            + " (resolved to "
                            + key
                            + ") but couldn't find it!"));
    if (!Objects.equals(value, regex)) {
      assertThat(value).matches(regex);
    }
  }

  /**
   * asserts that value of context entry with given key either equals or matches (regex) the given
   * regex string. Variable substitution is performed.
   *
   * <p>Special values can be used:
   *
   * @param key key of entry to check
   */
  @Dann("TGR prüfe Variable {tigerResolvedString} ist unbekannt")
  @Then("TGR assert variable {tigerResolvedString} is unknown")
  public void ctxtAssertVariableUnknown(final String key) {
    final Optional<String> optionalValue = TigerGlobalConfiguration.readStringOptional(key);
    assertThat(optionalValue)
        .withFailMessage(
            "Wanted to assert value of key {} (resolved to {}) is not set " + "but found value {}!",
            key,
            key,
            optionalValue)
        .isEmpty();
  }

  @Gegebensei("TGR zeige {word} Banner {tigerResolvedString}")
  @Given("TGR show {word} banner {tigerResolvedString}")
  public void tgrShowColoredBanner(String color, String text) {
    log.info("\n" + Banner.toBannerStrWithCOLOR(text, color.toUpperCase()));
  }

  @Gegebensei("TGR zeige {word} Text {tigerResolvedString}")
  @Given("TGR show {word} text {tigerResolvedString}")
  public void tgrShowColoredText(String color, String text) {
    log.info("\n" + Banner.toTextStr(text, color.toUpperCase()));
  }

  @Gegebensei("TGR zeige Banner {tigerResolvedString}")
  @Given("TGR show banner {tigerResolvedString}")
  public void tgrIWantToShowBanner(String text) {
    log.info("\n" + Banner.toBannerStrWithCOLOR(text, "WHITE"));
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

  @When(
      "TGR pause test run execution with message {tigerResolvedString} and message in case of error"
          + " {tigerResolvedString}")
  @Wenn(
      "TGR pausiere Testausführung mit Nachricht {tigerResolvedString} und Meldung im Fehlerfall"
          + " {tigerResolvedString}")
  public void tgrPauseExecutionWithMessageAndErrorMessage(String message, String errorMessage) {
    TigerDirector.pauseExecutionAndFailIfDesired(message, errorMessage);
  }

  @When("TGR show HTML Notification:")
  @Wenn("TGR zeige HTML Notification:")
  public void tgrShowHtmlNotification(String message) {
    final String bannerMessage = TigerGlobalConfiguration.resolvePlaceholders(message);
    if (TigerDirector.getLibConfig().isActivateWorkflowUi()) {
      TigerDirector.getTigerTestEnvMgr()
          .receiveTestEnvUpdate(
              TigerStatusUpdate.builder()
                  .bannerMessage(bannerMessage)
                  .bannerColor("green")
                  .bannerType(BannerType.STEP_WAIT)
                  .bannerIsHtml(true)
                  .build());
      await()
          .pollInterval(1, TimeUnit.SECONDS)
          .atMost(TigerDirector.getLibConfig().getPauseExecutionTimeoutSeconds(), TimeUnit.SECONDS)
          .until(() -> TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedOnWorkflowUi());
      TigerDirector.getTigerTestEnvMgr().resetConfirmationFromWorkflowUi();
    } else {
      log.warn("Workflow UI is not active! Can't display message '{}'", bannerMessage);
    }
  }

  @When("TGR assert {tigerResolvedString} matches {tigerResolvedString}")
  @Dann("TGR prüfe das {tigerResolvedString} mit {tigerResolvedString} überein stimmt")
  public void tgrAssertMatches(String value1, String value2) {
    if (!Objects.equals(value1, value2)) {
      assertThat(value1).matches(value2);
    }
  }

  /** Prints the value of the given variable to the System-out */
  @Dann("TGR gebe variable {tigerResolvedString} aus")
  @Then("TGR print variable {tigerResolvedString}")
  public void printVariable(String key) {
    final Optional<String> optionalValue = TigerGlobalConfiguration.readStringOptional(key);
    System.out.println(key + ": '" + optionalValue.orElse("This key is not set!") + "'"); // NOSONAR
  }

  /**
   * Stops the given server. If the server is not running or the server is not found, an exception
   * is thrown.
   *
   * @param servername The server to be stopped.
   */
  @Given("TGR stop server {tigerResolvedString}")
  public static void tgrStopServer(String servername) {
    final var server = TigerDirector.getTigerTestEnvMgr().getServers().get(servername);
    if (server == null) {
      throw new TigerServerNotFoundException(servername);
    }
    if (server.getStatus() != TigerServerStatus.RUNNING) {
      throw new TigerLibraryException(
          "Server with name "
              + servername
              + " is not running! Current status is "
              + server.getStatus());
    }
    log.trace("Starting shutdown at {}", LocalDateTime.now());
    server.stopServerAndCleanUp();
    log.trace("Shutdown complete at {} with status {}", LocalDateTime.now(), server.getStatus());
  }

  /**
   * Starts the given server. If the server is already running or the server is not found, an
   * exception is thrown.
   *
   * @param servername The server to be started.
   */
  @Given("TGR start server {tigerResolvedString}")
  public static void tgrStartServer(String servername) {
    final var server = TigerDirector.getTigerTestEnvMgr().getServers().get(servername);
    if (server == null) {
      throw new TigerServerNotFoundException(servername);
    }
    if (server.getStatus() != TigerServerStatus.STOPPED) {
      throw new TigerLibraryException(
          "Server with name "
              + servername
              + " is not stopped! Current status is "
              + server.getStatus());
    }
    server.start(TigerDirector.getTigerTestEnvMgr());
  }

  public static class TigerServerNotFoundException extends TigerLibraryException {
    public TigerServerNotFoundException(String servername) {
      super("Server with name " + servername + " not found!");
    }
  }
}
