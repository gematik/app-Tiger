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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;

/** Perceptual PNG comparison using RMSE (Root Mean Square Error). */
class ImageDiff {

  record FileRmse(String file, double rmse) {}

  record DiffResult(
      List<Path> changed,
      List<String> added,
      List<String> deleted,
      List<FileRmse> changedDetail,
      List<FileRmse> identicalDetail,
      int totalNew) {

    boolean hasChanges() {
      return !changed.isEmpty() || !deleted.isEmpty();
    }

    int changeCount() {
      return changed.size() + deleted.size();
    }
  }

  static DiffResult compare(Path newDir, Path oldDir, double threshold) throws IOException {
    var newNames = Fs.listPngNames(newDir);
    var oldNames = Fs.listPngNames(oldDir);
    var changed = new ArrayList<Path>();
    var added = new ArrayList<String>();
    var deleted = new ArrayList<String>();
    var changedDetail = new ArrayList<FileRmse>();
    var identicalDetail = new ArrayList<FileRmse>();

    for (var name : newNames) {
      if (!oldNames.contains(name)) {
        added.add(name);
        changed.add(newDir.resolve(name));
      } else {
        double rmse = computeRMSE(newDir.resolve(name), oldDir.resolve(name));
        if (rmse > threshold) {
          changedDetail.add(new FileRmse(name, rmse));
          changed.add(newDir.resolve(name));
        } else {
          identicalDetail.add(new FileRmse(name, rmse));
        }
      }
    }
    for (var name : oldNames) {
      if (!newNames.contains(name)) deleted.add(name);
    }

    return new DiffResult(changed, added, deleted, changedDetail, identicalDetail, newNames.size());
  }

  static double computeRMSE(Path a, Path b) throws IOException {
    BufferedImage imgA = ImageIO.read(a.toFile()), imgB = ImageIO.read(b.toFile());
    if (imgA == null || imgB == null) return 1.0;
    if (imgA.getWidth() != imgB.getWidth() || imgA.getHeight() != imgB.getHeight()) return 1.0;
    int w = imgA.getWidth(), h = imgA.getHeight();
    long sum = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int pa = imgA.getRGB(x, y), pb = imgB.getRGB(x, y);
        int dr = ((pa >> 16) & 0xFF) - ((pb >> 16) & 0xFF);
        int dg = ((pa >> 8) & 0xFF) - ((pb >> 8) & 0xFF);
        int db = (pa & 0xFF) - (pb & 0xFF);
        sum += (long) dr * dr + (long) dg * dg + (long) db * db;
      }
    }
    return Math.sqrt((double) sum / ((long) w * h * 3)) / 255.0;
  }
}
