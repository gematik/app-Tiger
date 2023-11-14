/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.config;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

import de.gematik.rbellogger.RbelOptions;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ResetTigerConfigurationExtension implements BeforeAllCallback, AfterAllCallback {

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    if (findResetAnnotation(extensionContext)
        .map(ResetTigerConfiguration::beforeAllMethods)
        .orElse(false)) {
      reset();
    }
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    if (findResetAnnotation(extensionContext)
        .map(ResetTigerConfiguration::afterAllMethods)
        .orElse(false)) {
      reset();
    }
  }

  private static void reset() {
    TigerGlobalConfiguration.reset();
    RbelOptions.reset();
  }

  private Optional<ResetTigerConfiguration> findResetAnnotation(ExtensionContext extensionContext) {
    return findAnnotation(extensionContext.getTestClass(), ResetTigerConfiguration.class);
  }
}
