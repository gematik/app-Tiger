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

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Retain command — deletes old baselines beyond the retention limit. */
@Slf4j
class RetainCommand {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Config cfg;
  private final Store store;

  RetainCommand(Config cfg) {
    this(cfg, null);
  }

  RetainCommand(Config cfg, Store store) {
    cfg.requireStoreUrl();
    this.cfg = cfg;
    this.store = store != null ? store : new Store(cfg);
  }

  int run() throws Exception {
    var workDir = Fs.createSecureTempDir("screenshots-retain");
    try {
      var histFile = workDir.resolve(Manifest.MANIFEST_HISTORY_JSON);
      if (!store.download(Manifest.MANIFEST_HISTORY_JSON, histFile)) {
        log.info("No history — nothing to clean up.");
        return 0;
      }
      var baselines =
          MAPPER.readValue(histFile.toFile(), new TypeReference<java.util.List<String>>() {});
      if (baselines.size() <= cfg.keepBaselines()) {
        log.info("{} baselines <= {} — nothing to delete.", baselines.size(), cfg.keepBaselines());
        return 0;
      }

      log.atInfo()
          .addArgument(baselines::size)
          .addArgument(cfg::keepBaselines)
          .log("Found {}, keeping {}.");
      for (int i = cfg.keepBaselines(); i < baselines.size(); i++) {
        log.info("Deleting: {}", baselines.get(i));
        store.delete(baselines.get(i));
      }

      var trimmed = baselines.subList(0, cfg.keepBaselines());
      var out = workDir.resolve("trimmed.json");
      MAPPER.writeValue(out.toFile(), trimmed);
      store.upload(out, Manifest.MANIFEST_HISTORY_JSON);
      log.info("Retention complete.");
      return 0;
    } finally {
      Fs.deleteTree(workDir);
    }
  }
}
