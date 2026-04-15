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

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.*;
import org.apache.commons.io.FileUtils;

/** Filesystem and zip utilities. */
class Fs {

  static final String DELETED_TXT = "deleted.txt";
  private static final String PNG_EXTENSION = ".png";

  /** Creates a temp directory with owner-only permissions (rwx------) where supported. */
  @SuppressWarnings("java:S5443")
  static Path createSecureTempDir(String prefix) throws IOException {
    try {
      var perms =
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
      return Files.createTempDirectory(prefix, perms);
    } catch (UnsupportedOperationException e) {
      // Non-POSIX filesystem (e.g. Windows) — fall back to default permissions
      return Files.createTempDirectory(prefix);
    }
  }

  static List<Path> listPngs(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) return List.of();
    try (var s = Files.list(dir)) {
      return s.filter(p -> p.toString().toLowerCase().endsWith(PNG_EXTENSION)).sorted().toList();
    }
  }

  static Set<String> listPngNames(Path dir) throws IOException {
    return listPngs(dir).stream()
        .map(p -> p.getFileName().toString())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  static void applyDeletions(Path dir) throws IOException {
    var f = dir.resolve(DELETED_TXT);
    if (Files.exists(f)) {
      for (var line : Files.readAllLines(f)) Files.deleteIfExists(dir.resolve(line.trim()));
      Files.delete(f);
    }
  }

  static void deleteTree(Path dir) {
    FileUtils.deleteQuietly(dir.toFile());
  }

  static void createZip(Path zipFile, List<Path> files) throws IOException {
    try (var zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
      for (var f : files) {
        zos.putNextEntry(new ZipEntry(f.getFileName().toString()));
        Files.copy(f, zos);
        zos.closeEntry();
      }
    }
  }

  /** Max number of entries allowed in an archive (screenshot sets are ~65 files). */
  private static final int MAX_ZIP_ENTRIES = 1000;

  /** Max total decompressed size (100 MB). */
  private static final long MAX_ZIP_SIZE = 100L * 1024 * 1024;

  @SuppressWarnings("java:S5042")
  static void extractZip(Path zipFile, Path destDir) throws IOException {
    try (var zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
      var normalizedDestDir = destDir.normalize();
      ZipEntry e;
      int entryCount = 0;
      long totalSize = 0;
      while ((e = zis.getNextEntry()) != null) {
        if (++entryCount > MAX_ZIP_ENTRIES) {
          throw new IOException("Too many zip entries (max " + MAX_ZIP_ENTRIES + ")");
        }
        var dest = destDir.resolve(e.getName()).normalize();
        if (!dest.startsWith(normalizedDestDir)) {
          throw new IOException("Zip entry outside target directory: " + e.getName());
        }
        totalSize += Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
        if (totalSize > MAX_ZIP_SIZE) {
          throw new IOException(
              "Decompressed size exceeds limit (" + MAX_ZIP_SIZE / (1024 * 1024) + " MB)");
        }
      }
    }
  }
}
