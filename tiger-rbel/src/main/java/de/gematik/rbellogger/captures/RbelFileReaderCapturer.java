/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.captures;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.util.RbelFileWriter;
import java.io.UncheckedIOException;
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
                .convertFromRbelFile(Paths.get(rbelFile));
            return this;
        } catch (UncheckedIOException e) {
            throw new RbelFileIoException("Error while reading from rbel-file with path '" + rbelFile + "'", e);
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
