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
package de.gematik.test.tiger.common.web;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.ThrowingConsumer;

@Slf4j
public class TigerBrowserUtil {

  static ThrowingConsumer<String[]> commandExecutor = Runtime.getRuntime()::exec;

  static BrowserOpener desktopBrowser =
      uri -> {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE)) {
          Desktop.getDesktop().browse(uri);
        } else {
          throw new UnsupportedOperationException("Desktop browsing not supported");
        }
      };

  @FunctionalInterface
  interface BrowserOpener {
    void open(URI uri) throws IOException;
  }

  private TigerBrowserUtil() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static void openUrlInBrowser(String url, String purpose) {
    try {
      URI uri;
      if (url.startsWith("http")) {
        uri = new URI(url);
      } else {
        uri = new File(url).toURI();
      }

      if (tryOpen("Java Desktop API", purpose, url, () -> desktopBrowser.open(uri))) return;
      if (tryOpen(
          "xdg-open",
          purpose,
          url,
          () -> commandExecutor.accept(new String[] {"xdg-open", uri.toString()}))) return;
      if (tryOpen(
          "wslview",
          purpose,
          url,
          () -> commandExecutor.accept(new String[] {"wslview", uri.toString()}))) return;

      String os = System.getProperty("os.name").toLowerCase();
      if (os.contains("win")) {
        commandExecutor.accept(
            new String[] {"rundll32", "url.dll,FileProtocolHandler", uri.toString()});
      } else if (os.contains("mac")) {
        commandExecutor.accept(new String[] {"open", uri.toString()});
      } else {
        log.error("Unable to open browser for {}: no working method found", purpose);
        return;
      }
      log.info(Ansi.colorize(purpose + " " + url, RbelAnsiColors.BLUE_BOLD));
    } catch (HeadlessException hex) {
      log.error("Unable to start " + purpose + " on a headless server!", hex);
    } catch (RuntimeException | URISyntaxException e) {
      log.error(
          "Exception while trying to start browser for "
              + purpose
              + ", still continuing with test run",
          e);
    }
  }

  private static boolean tryOpen(
      String method, String purpose, String url, ThrowingRunnable action) {
    try {
      log.info("Starting {} via {}", purpose, method);
      action.run();
      log.info(Ansi.colorize(purpose + " " + url, RbelAnsiColors.BLUE_BOLD));
      return true;
    } catch (RuntimeException | IOException e) {
      log.debug("{} failed ({}), trying next method", method, e.getMessage());
      return false;
    }
  }

  @FunctionalInterface
  interface ThrowingRunnable {
    void run() throws IOException;
  }
}
