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

@Slf4j
public class TigerBrowserUtil {

  private TigerBrowserUtil() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static void openUrlInBrowser(String url, String purpose) {
    try {
      URI uri;
      if (url.startsWith("http")) {
        uri = new URI(url);
      } else {
        File file = new File(url);
        uri = file.toURI();
      }

      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE)) {
        Desktop desktop = Desktop.getDesktop();
        log.info("Starting " + purpose + " via Java Desktop API");
        desktop.browse(uri);
        log.info(Ansi.colorize(purpose + "{}", RbelAnsiColors.BLUE_BOLD), url);
      } else {
        String command;
        String operatingSystemName = System.getProperty("os.name").toLowerCase();
        if (operatingSystemName.contains("nix") || operatingSystemName.contains("nux")) {
          command = "xdg-open " + url;
        } else if (operatingSystemName.contains("win")) {
          command = "rundll32 url.dll,FileProtocolHandler " + url;
        } else if (operatingSystemName.contains("mac")) {
          command = "open " + url;
        } else {
          log.error("Unknown operation system '{}'", operatingSystemName);
          return;
        }
        log.info("Starting " + purpose + " via '{}'", command);
        Runtime.getRuntime().exec(command);
        log.info(Ansi.colorize(purpose + " " + url, RbelAnsiColors.BLUE_BOLD));
      }
    } catch (HeadlessException hex) {
      log.error("Unable to start " + purpose + " on a headless server!", hex);
    } catch (RuntimeException | URISyntaxException | IOException e) {
      log.error(
          "Exception while trying to start browser for "
              + purpose
              + ", still continuing with test run",
          e);
    }
  }
}
