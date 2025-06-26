/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.lib;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import java.io.IOException;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TigerInitializer {
  private static final Object startupMutex = new Object();
  private static RuntimeException tigerStartupFailedException;

  public void runWithSafelyInitialized(Runnable r) {
    synchronized (startupMutex) {
      if (!TigerDirector.isInitialized()) {
        showTigerVersion();
        initializeTiger();
        TigerDirector.assertThatTigerIsInitialized();
      } else {
        log.info("Tiger is already initialized, skipping initialization");
      }
    }
    r.run();
  }

  private synchronized void initializeTiger() {
    if (tigerStartupFailedException != null) {
      return;
    }
    try {
      log.info("Initializing Tiger");
      TigerDirector.registerShutdownHook();
      TigerDirector.start();
    } catch (RuntimeException rte) {
      tigerStartupFailedException = rte;
      throw tigerStartupFailedException;
    }
  }

  private void showTigerVersion() {
    log.info(
        Ansi.colorize(
            "Starting Tiger version " + getTigerVersionString(), RbelAnsiColors.GREEN_BRIGHT));
  }

  public String getTigerVersionString() {
    try {
      Properties p = new Properties();
      p.load(TigerInitializer.class.getResourceAsStream("/build.properties"));
      String version = p.getProperty("tiger.version");
      if (version.equals("${project.version}")) {
        version = "UNKNOWN";
      }
      return version + "-" + p.getProperty("tiger.build.timestamp");
    } catch (RuntimeException | IOException ignored) {
      return "UNKNOWN";
    }
  }
}
