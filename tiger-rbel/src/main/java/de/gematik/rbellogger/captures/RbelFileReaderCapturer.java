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
package de.gematik.rbellogger.captures;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
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
          .convertFromRbelFile(
              Files.readString(Paths.get(rbelFile), StandardCharsets.UTF_8), Optional.empty());
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

  private static class RbelFileIoException extends GenericTigerException {
    public RbelFileIoException(String s, Exception e) {
      super(s, e);
    }
  }
}
