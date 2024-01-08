/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.toolschecker;

import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/** Checks if all the dependencies required to run the tiger test suite are fulfilled. */
@NoArgsConstructor
public class DependencyToolsChecker {

  public DependencyCheckResult areNecessaryDependenciesAvailable() {
    if (Boolean.FALSE.equals(
        TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE
            .getValueOrDefault())) {
      return new DependencyCheckResult(true, "");
    }
    if (isItWindows()) {
      return new DependencyCheckResult(true, "");
    } else {
      if (hasLsof()) {
        return new DependencyCheckResult(true, "");
      } else {
        return new DependencyCheckResult(
            false, "missing required 'lsof' command in your operating system");
      }
    }
  }

  @SneakyThrows
  protected boolean hasLsof() {
    Process process = Runtime.getRuntime().exec("which lsof");
    int exitCode = process.waitFor();
    return exitCode == 0;
  }

  private boolean isItWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }
}
