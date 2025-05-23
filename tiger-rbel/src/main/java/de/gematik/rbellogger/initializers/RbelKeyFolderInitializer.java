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
package de.gematik.rbellogger.initializers;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.exceptions.RbelPkiException;
import de.gematik.rbellogger.key.IdentityBackedRbelKey;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RbelKeyFolderInitializer implements Consumer<RbelConverter> {

  private final String keyFolderPath;

  @Override
  public void accept(RbelConverter rbelConverter) {
    AtomicReference<Path> currentFile = new AtomicReference<>();
    try (final Stream<Path> fileStream = Files.walk(Path.of(keyFolderPath))) {
      fileStream
          .map(path -> setCurrentFile(path, currentFile))
          .map(Path::toFile)
          .filter(File::isFile)
          .filter(File::canRead)
          .filter(file -> file.getName().endsWith(".p12"))
          .map(
              file ->
                  TigerPkiIdentityLoader.loadRbelPkiIdentityWithGuessedPassword(file)
                      .withKeyId(Optional.ofNullable(file.getName().split("\\.")[0])))
          .map(IdentityBackedRbelKey::generateRbelKeyPairForIdentity)
          .flatMap(List::stream)
          .forEach(rbelConverter.getRbelKeyManager()::addKey);
    } catch (IOException e) {
      throw new RbelPkiException(
          "Error while initializing keys, failed at file '"
              + currentFile.get().toAbsolutePath()
              + "'",
          e);
    }
  }

  private Path setCurrentFile(Path path, AtomicReference<Path> currentFile) {
    currentFile.set(path);
    return path;
  }
}
