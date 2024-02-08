/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.captures;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.util.RbelFileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.Builder;

public class RbelFileReaderCapturer extends RbelCapturer {

  private final String rbelFile;

  @Builder
  public RbelFileReaderCapturer(RbelConverter rbelConverter, String rbelFile) {
    super(rbelConverter);
    this.rbelFile = rbelFile;
  }

  @Override
  public RbelCapturer initialize() {
    try {
      new RbelFileWriter(getRbelConverter())
          .convertFromRbelFile(Files.readString(Paths.get(rbelFile), StandardCharsets.UTF_8));
      return this;
    } catch (UncheckedIOException | IOException e) {
      throw new RbelFileIoException(
          "Error while reading from rbel-file with path '" + rbelFile + "'", e);
    }
  }

  @Override
  public void close() throws Exception {
    // no open handles
  }

  private static class RbelFileIoException extends RuntimeException {
    public RbelFileIoException(String s, Exception e) {
      super(s, e);
    }
  }
}
