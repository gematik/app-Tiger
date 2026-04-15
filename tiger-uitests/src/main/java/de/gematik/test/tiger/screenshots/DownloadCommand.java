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

import java.io.IOException;
import java.nio.file.*;
import lombok.extern.slf4j.Slf4j;

/** Download command — downloads and reconstructs screenshots from the archive. */
@Slf4j
class DownloadCommand {

  private final Config cfg;
  private final Store store;

  DownloadCommand(Config cfg) {
    cfg.requireStoreUrl();
    this.cfg = cfg;
    this.store = new Store(cfg);
  }

  int run() throws Exception {
    var workDir = Fs.createSecureTempDir("screenshots-download");
    try {
      var remote = downloadRemoteManifest(workDir);
      if (remote == null) return 2;

      if (isCacheUpToDate(remote)) return 0;

      int localSeq = downloadBaselineIfNeeded(workDir, remote);
      if (localSeq < 0) return 2;

      if (!applyMissingDeltas(workDir, remote, localSeq)) return 2;

      Files.copy(
          workDir.resolve(Manifest.MANIFEST_JSON),
          cfg.screenshotDir().resolve(Manifest.CACHE_MANIFEST_JSON),
          StandardCopyOption.REPLACE_EXISTING);
      log.info("Cache updated. Screenshots are current.");
      return 0;
    } finally {
      Fs.deleteTree(workDir);
    }
  }

  private Manifest downloadRemoteManifest(Path workDir) throws IOException {
    var file = workDir.resolve(Manifest.MANIFEST_JSON);
    if (!store.download(Manifest.MANIFEST_JSON, file)) {
      log.error("Could not download manifest.json");
      return null;
    }
    var m = Manifest.read(file);
    log.atInfo()
        .addArgument(m::baseline)
        .addArgument(m::sequence)
        .log("Remote: baseline={}, sequence={}");
    return m;
  }

  private boolean isCacheUpToDate(Manifest remote) throws IOException {
    var cachePath = cfg.screenshotDir().resolve(Manifest.CACHE_MANIFEST_JSON);
    if (!Files.exists(cachePath)) {
      log.info("No local cache.");
      return false;
    }
    var local = Manifest.read(cachePath);
    if (remote.baseline().equals(local.baseline()) && remote.sequence() == local.sequence()) {
      log.info("Cached screenshots are up-to-date — skipping.");
      return true;
    }
    log.info("Cache is stale.");
    return false;
  }

  private int downloadBaselineIfNeeded(Path workDir, Manifest remote) throws Exception {
    Files.createDirectories(cfg.screenshotDir());
    var cachePath = cfg.screenshotDir().resolve(Manifest.CACHE_MANIFEST_JSON);
    if (Files.exists(cachePath)) {
      var local = Manifest.read(cachePath);
      if (remote.baseline().equals(local.baseline())) return local.sequence();
    }

    log.atInfo().addArgument(remote::baseline).log("Downloading baseline: {}");
    var zip = workDir.resolve(Manifest.BASELINE_ZIP);
    if (!store.download(remote.baseline(), zip)) {
      log.error("Could not download baseline");
      return -1;
    }
    for (var png : Fs.listPngs(cfg.screenshotDir())) Files.deleteIfExists(png);
    Fs.extractZip(zip, cfg.screenshotDir());
    log.info("Baseline extracted.");
    return 0;
  }

  private boolean applyMissingDeltas(Path workDir, Manifest remote, int fromSeq) throws Exception {
    for (int i = fromSeq + 1; i <= remote.sequence(); i++) {
      var name = PublishCommand.deltaName(i);
      log.info("Downloading delta: {}", name);
      var zip = workDir.resolve(Manifest.DELTA_ZIP);
      if (!store.download(name, zip)) {
        log.error("Could not download delta {}", name);
        return false;
      }
      Fs.extractZip(zip, cfg.screenshotDir());
      Fs.applyDeletions(cfg.screenshotDir());
      log.info("Applied delta {}", name);
    }
    return true;
  }
}
