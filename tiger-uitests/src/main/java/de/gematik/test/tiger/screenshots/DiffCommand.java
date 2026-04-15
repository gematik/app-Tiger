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

/** Diff command — standalone image comparison between two directories. */
class DiffCommand {

  private final Config cfg;
  private final String[] args;

  DiffCommand(Config cfg, String[] args) {
    this.cfg = cfg;
    this.args = args;
  }

  int run() throws Exception {
    Path newDir = null, oldDir = null;
    for (var a : args) {
      if (a.startsWith("--")) continue;
      if (newDir == null) newDir = Path.of(a);
      else if (oldDir == null) oldDir = Path.of(a);
    }
    if (newDir == null || oldDir == null) {
      System.err.println("Usage: diff <new-dir> <old-dir> [--threshold=N]");
      return 1;
    }
    var r = ImageDiff.compare(newDir, oldDir, cfg.rmseThreshold());
    for (var e : r.changedDetail()) System.out.printf("CHANGED\t%s\t%.6f%n", e.file(), e.rmse());
    for (var n : r.added()) System.out.println("NEW\t" + n);
    for (var d : r.deleted()) System.out.println("DELETED\t" + d);
    for (var e : r.identicalDetail())
      System.out.printf("IDENTICAL\t%s\t%.6f%n", e.file(), e.rmse());
    return 0;
  }
}
