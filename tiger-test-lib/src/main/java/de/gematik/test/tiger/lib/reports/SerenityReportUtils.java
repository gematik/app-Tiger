/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.reports;

import de.gematik.test.tiger.lib.TigerDirector;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import net.thucydides.core.steps.StepEventBus;

@Slf4j
public class SerenityReportUtils {

  public static void addCustomData(final String title, final String content) {
    if (TigerDirector.isSerenityAvailable()) {
      if (StepEventBus.getEventBus().isBaseStepListenerRegistered()) {
        Serenity.recordReportData().withTitle(title).andContents(content);
      }
    }
    log.info(String.format("%s: %s", title, content));
  }
}
