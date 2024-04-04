/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter.initializers;

import de.gematik.rbellogger.converter.RbelConverter;
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
              + currentFile.get().toAbsolutePath().toString()
              + "'",
          e);
    }
  }

  private Path setCurrentFile(Path path, AtomicReference<Path> currentFile) {
    currentFile.set(path);
    return path;
  }
}
