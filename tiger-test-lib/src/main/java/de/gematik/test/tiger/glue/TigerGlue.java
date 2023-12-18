/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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
   * is performed. This value will only be accessible from this exact thread.
   *
   * @param key key of the context
   * @param value value for the context entry with given key
   */
  @Wenn("TGR setze lokale Variable {tigerResolvedString} auf {tigerResolvedString}")
  @When("TGR set local variable {tigerResolvedString} to {tigerResolvedString}")
  public void ctxtISetLocalVariableTo(final String key, final String value) {
    log.debug("Setting local variable {} to '{}'", key, value);
    TigerGlobalConfiguration.putValue(key, value, SourceType.THREAD_CONTEXT);
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

  @When("TGR pause test run execution with message {tigerResolvedString}")
  @Wenn("TGR pausiere Testausführung mit Nachricht {tigerResolvedString}")
  public void tgrPauseExecutionWithMessage(String message) {
    TigerDirector.pauseExecution(message);
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
}
