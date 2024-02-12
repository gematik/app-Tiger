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

package de.gematik.test.tiger.common.toolschecker;

import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/** Checks if all the dependencies required to run the tiger test suite are fulfilled. */
@NoArgsConstructor
public class DependencyToolsChecker {

  public DependencyCheckResult areNecessaryDependenciesAvailable() {
    if (Boolean.FALSE.equals(
        TigerConfigurationKeys.TRAFFIC_VISUALIZATION_ACTIVE.getValueOrDefault())) {
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
