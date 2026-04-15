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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/** Publish command — compares new screenshots against the archive, publishes delta or baseline. */
@Slf4j
class PublishCommand {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Config cfg;
  private final Store store;
  private boolean forceBaseline;

  PublishCommand(Config cfg) {
    cfg.requireStoreUrl();
    this.cfg = cfg;
    this.store = new Store(cfg);
    this.forceBaseline = cfg.forceBaseline();
  }

  int run() throws Exception {
    var workDir = Fs.createSecureTempDir("screenshots-publish");
    try {
      var state = reconstructCurrentState(workDir);
      var diff = compareScreenshots(state.currentDir);

      if (!forceBaseline && !diff.hasChanges()) {
        log.info("No visual changes detected — nothing to publish.");
        return 0;
      }

      var ts = Manifest.nowTimestamp();
      if (shouldPublishBaseline(diff, state.sequence)) {
        publishBaseline(workDir, state, ts);
      } else {
        publishDelta(workDir, diff, state, ts);
      }
      return 0;
    } finally {
      Fs.deleteTree(workDir);
    }
  }

  record ArchiveState(Path currentDir, String baselineName, int sequence) {}

  private ArchiveState reconstructCurrentState(Path workDir) throws Exception {
    var currentDir = Files.createDirectories(workDir.resolve("current"));
    var manifestFile = workDir.resolve(Manifest.MANIFEST_JSON);

    if (!store.download(Manifest.MANIFEST_JSON, manifestFile)) {
      log.info("No manifest found — first run.");
      forceBaseline = true;
      return new ArchiveState(currentDir, null, 0);
    }

    log.info("Downloaded manifest.json");
    var manifest = Manifest.read(manifestFile);

    if (!store.download(manifest.baseline(), workDir.resolve(Manifest.BASELINE_ZIP))) {
      log.info("WARNING: Could not download baseline — treating as first run.");
      forceBaseline = true;
      return new ArchiveState(currentDir, null, 0);
    }

    Fs.extractZip(workDir.resolve(Manifest.BASELINE_ZIP), currentDir);
    log.atInfo().addArgument(manifest::baseline).log("Reconstructed baseline: {}");

    for (int i = 1; i <= manifest.sequence(); i++) {
      var name = deltaName(i);
      if (store.download(name, workDir.resolve(Manifest.DELTA_ZIP))) {
        Fs.extractZip(workDir.resolve(Manifest.DELTA_ZIP), currentDir);
        Fs.applyDeletions(currentDir);
        log.info("Applied delta {}", name);
      }
    }
    return new ArchiveState(currentDir, manifest.baseline(), manifest.sequence());
  }

  private ImageDiff.DiffResult compareScreenshots(Path currentDir) throws IOException {
    if (forceBaseline) {
      log.info("FORCE_BASELINE — skipping comparison.");
      int n = Fs.listPngs(cfg.screenshotDir()).size();
      return new ImageDiff.DiffResult(List.of(), List.of(), List.of(), List.of(), List.of(), n);
    }

    var diff = ImageDiff.compare(cfg.screenshotDir(), currentDir, cfg.rmseThreshold());
    diff.changedDetail()
        .forEach(
            e -> log.info("  CHANGED: {} (RMSE={})", e.file(), String.format("%.6f", e.rmse())));
    diff.added().forEach(n -> log.info("  NEW: {}", n));
    diff.deleted().forEach(d -> log.info("  DELETED: {}", d));
    return diff;
  }

  private boolean shouldPublishBaseline(ImageDiff.DiffResult diff, int currentSeq) {
    if (forceBaseline) return true;
    int total = diff.totalNew() + diff.deleted().size();
    if (total > 0 && 100 * diff.changeCount() / total > cfg.changePercentThreshold()) {
      log.atInfo()
          .addArgument(cfg::changePercentThreshold)
          .log("Change ratio exceeds {}% — new baseline.");
      return true;
    }
    if (currentSeq + 1 > cfg.maxDeltaChain()) {
      log.atInfo().addArgument(cfg::maxDeltaChain).log("Delta chain exceeds {} — new baseline.");
      return true;
    }
    return false;
  }

  private void publishBaseline(Path workDir, ArchiveState state, String ts) throws Exception {
    var zipName = Manifest.BASELINE_PREFIX + ts + ".zip";
    var zipFile = workDir.resolve(zipName);
    Fs.createZip(zipFile, Fs.listPngs(cfg.screenshotDir()));
    var sizeK = Files.size(zipFile) / 1024;
    log.info("Created baseline: {} ({}K)", zipName, sizeK);

    store.upload(zipFile, zipName);
    new Manifest(zipName, 0, ts).write(workDir.resolve(Manifest.MANIFEST_JSON));
    store.upload(workDir.resolve(Manifest.MANIFEST_JSON), Manifest.MANIFEST_JSON);

    updateHistory(workDir, zipName);
    for (int i = 1; i <= state.sequence(); i++) store.delete(deltaName(i));
    new RetainCommand(cfg, store).run();

    log.info("Published new baseline. Delta chain reset to 0.");
  }

  private void publishDelta(Path workDir, ImageDiff.DiffResult diff, ArchiveState state, String ts)
      throws Exception {
    int nextSeq = state.sequence() + 1;
    var deltaDir = Files.createDirectories(workDir.resolve("delta-contents"));

    if (!diff.deleted().isEmpty()) Files.write(deltaDir.resolve(Fs.DELETED_TXT), diff.deleted());

    var files = new ArrayList<Path>();
    for (var src : diff.changed()) {
      var dest = deltaDir.resolve(src.getFileName());
      Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
      files.add(dest);
    }
    if (Files.exists(deltaDir.resolve(Fs.DELETED_TXT))) files.add(deltaDir.resolve(Fs.DELETED_TXT));

    var name = deltaName(nextSeq);
    var zipFile = workDir.resolve(name);
    Fs.createZip(zipFile, files);
    log.info(
        "Created delta: {} ({}K, {} changed, {} deleted)",
        name,
        Files.size(zipFile) / 1024,
        diff.changed().size(),
        diff.deleted().size());

    store.upload(zipFile, name);
    new Manifest(state.baselineName(), nextSeq, ts).write(workDir.resolve(Manifest.MANIFEST_JSON));
    store.upload(workDir.resolve(Manifest.MANIFEST_JSON), Manifest.MANIFEST_JSON);

    log.info("Published delta {}. Chain length: {}.", name, nextSeq);
  }

  private void updateHistory(Path workDir, String newBaseline) throws Exception {
    var histFile = workDir.resolve(Manifest.MANIFEST_HISTORY_JSON);
    var history =
        store.download(Manifest.MANIFEST_HISTORY_JSON, histFile)
            ? new ArrayList<>(
                MAPPER.readValue(histFile.toFile(), new TypeReference<List<String>>() {}))
            : new ArrayList<String>();
    history.add(0, newBaseline);
    var out = workDir.resolve("new-history.json");
    MAPPER.writeValue(out.toFile(), history);
    store.upload(out, Manifest.MANIFEST_HISTORY_JSON);
  }

  static String deltaName(int seq) {
    return String.format("screenshots-delta-%03d.zip", seq);
  }
}
