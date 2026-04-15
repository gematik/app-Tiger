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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TigerBrowserUtilTest {

  static final String TEST_URL = "https://example.com";

  static final ThrowingConsumer<String[]> DEFAULT_COMMAND_EXECUTOR =
      TigerBrowserUtil.commandExecutor;
  static final TigerBrowserUtil.BrowserOpener DEFAULT_DESKTOP_BROWSER =
      TigerBrowserUtil.desktopBrowser;

  @AfterEach
  void restoreDefaults() {
    TigerBrowserUtil.commandExecutor = DEFAULT_COMMAND_EXECUTOR;
    TigerBrowserUtil.desktopBrowser = DEFAULT_DESKTOP_BROWSER;
  }

  // helper that throws for a given command name, otherwise captures it
  private static ThrowingConsumer<String[]> throwingFor(String command, List<String[]> captured) {
    return cmd -> {
      captured.add(cmd);
      if (Arrays.asList(cmd).contains(command)) {
        throw new IllegalArgumentException(command + " not found");
      }
    };
  }

  @Test
  void openUrlInBrowser_triesXdgOpen_whenDesktopFails() {
    TigerBrowserUtil.desktopBrowser =
        uri -> {
          throw new UnsupportedOperationException("no desktop");
        };
    List<String[]> captured = new ArrayList<>();
    TigerBrowserUtil.commandExecutor = captured::add;

    TigerBrowserUtil.openUrlInBrowser(TEST_URL, "test");

    assertThat(captured.get(0)).containsExactly("xdg-open", TEST_URL);
  }

  @Test
  void openUrlInBrowser_succeedsViaDesktop_withoutCallingAnyCommand() {
    List<URI> browsed = new ArrayList<>();
    TigerBrowserUtil.desktopBrowser = browsed::add;
    List<String[]> captured = new ArrayList<>();
    TigerBrowserUtil.commandExecutor = captured::add;

    TigerBrowserUtil.openUrlInBrowser(TEST_URL, "test");

    assertThat(browsed).hasSize(1);
    assertThat(captured).isEmpty();
  }

  @Test
  void openUrlInBrowser_fallsBackToWslview_whenXdgOpenFails() {
    TigerBrowserUtil.desktopBrowser =
        uri -> {
          throw new UnsupportedOperationException("no desktop");
        };
    List<String[]> captured = new ArrayList<>();
    TigerBrowserUtil.commandExecutor = throwingFor("xdg-open", captured);

    TigerBrowserUtil.openUrlInBrowser(TEST_URL, "test");

    assertThat(captured).hasSize(2);
    assertThat(captured.get(0)).containsExactly("xdg-open", TEST_URL);
    assertThat(captured.get(1)).containsExactly("wslview", TEST_URL);
  }

  @Test
  void openUrlInBrowser_fallsBackToOsCommand_whenXdgOpenAndWslviewFail() {
    TigerBrowserUtil.desktopBrowser =
        uri -> {
          throw new UnsupportedOperationException("no desktop");
        };
    List<String[]> captured = new ArrayList<>();
    TigerBrowserUtil.commandExecutor =
        cmd -> {
          captured.add(cmd);
          if (Arrays.asList(cmd).contains("xdg-open") || Arrays.asList(cmd).contains("wslview")) {
            throw new RuntimeException("not found");
          }
        };

    TigerBrowserUtil.openUrlInBrowser(TEST_URL, "test");

    assertThat(captured.get(0)).containsExactly("xdg-open", TEST_URL);
    assertThat(captured.get(1)).containsExactly("wslview", TEST_URL);
  }

  @Test
  void openUrlInBrowser_passesCorrectUrl_forFileUrl() {
    TigerBrowserUtil.desktopBrowser =
        uri -> {
          throw new UnsupportedOperationException("no desktop");
        };
    List<String[]> captured = new ArrayList<>();
    TigerBrowserUtil.commandExecutor = captured::add;

    TigerBrowserUtil.openUrlInBrowser("/tmp/report.html", "test");

    assertThat(captured.get(0)[0]).isEqualTo("xdg-open");
    assertThat(captured.get(0)[1]).startsWith("file:/");
  }
}
