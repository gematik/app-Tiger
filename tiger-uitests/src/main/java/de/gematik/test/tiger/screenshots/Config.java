/*
 * Copyright 2026 gematik GmbH
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
package de.gematik.test.tiger.screenshots;

import java.nio.file.Path;
import lombok.Builder;

/** CLI configuration, parsed from environment variables and/or flags. */
@Builder(toBuilder = true)
record Config(
    String storeUrl,
    String storeUser,
    String storePassword,
    Path screenshotDir,
    boolean forceBaseline,
    double rmseThreshold,
    int maxDeltaChain,
    int changePercentThreshold,
    int keepBaselines,
    boolean dryRun) {

  static Config parse(String[] args) {
    var b =
        Config.builder()
            .storeUrl(env("SCREENSHOT_STORE_URL", null))
            .storeUser(env("SCREENSHOT_STORE_USER", null))
            .storePassword(env("SCREENSHOT_STORE_PASSWORD", null))
            .screenshotDir(Path.of(env("SCREENSHOT_DIR", "doc/user_manual/screenshots")))
            .forceBaseline(flag("FORCE_BASELINE"))
            .rmseThreshold(Double.parseDouble(env("RMSE_THRESHOLD", "0.15")))
            .maxDeltaChain(Integer.parseInt(env("MAX_DELTA_CHAIN", "10")))
            .changePercentThreshold(Integer.parseInt(env("CHANGE_PERCENT_THRESHOLD", "50")))
            .keepBaselines(Integer.parseInt(env("KEEP_BASELINES", "3")))
            .dryRun(flag("DRY_RUN"));

    for (var a : args) {
      if (a.startsWith("--store-url=")) b.storeUrl(val(a));
      if (a.startsWith("--store-user=")) b.storeUser(val(a));
      if (a.startsWith("--store-password=")) b.storePassword(val(a));
      if (a.startsWith("--screenshot-dir=")) b.screenshotDir(Path.of(val(a)));
      if (a.equals("--force-baseline")) b.forceBaseline(true);
      if (a.startsWith("--threshold=")) b.rmseThreshold(Double.parseDouble(val(a)));
      if (a.startsWith("--max-chain=")) b.maxDeltaChain(Integer.parseInt(val(a)));
      if (a.startsWith("--change-percent=")) b.changePercentThreshold(Integer.parseInt(val(a)));
      if (a.startsWith("--keep-baselines=")) b.keepBaselines(Integer.parseInt(val(a)));
      if (a.equals("--dry-run")) b.dryRun(true);
    }

    var cfg = b.build();
    var url = cfg.storeUrl();
    if (url != null && url.endsWith("/")) {
      cfg = cfg.toBuilder().storeUrl(url.substring(0, url.length() - 1)).build();
    }
    return cfg;
  }

  void requireStoreUrl() {
    if (storeUrl == null || storeUrl.isEmpty()) {
      System.err.println("ERROR: --store-url / SCREENSHOT_STORE_URL is required.");
      System.exit(1);
    }
  }

  private static String val(String a) {
    return a.substring(a.indexOf('=') + 1);
  }

  private static String env(String k, String fb) {
    var v = System.getenv(k);
    return v != null && !v.isEmpty() ? v : fb;
  }

  private static boolean flag(String k) {
    return "true".equalsIgnoreCase(env(k, "false"));
  }
}
