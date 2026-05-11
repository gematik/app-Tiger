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
package de.gematik.rbellogger.file;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.common.util.TigerVersionProvider;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

/** Serializes {@link RbelElement} messages into .tgr file format (one JSON line per message). */
@Slf4j
public class RbelFileWriter {

  private static final String FILE_DIVIDER = "\n";

  public static final String RAW_MESSAGE_CONTENT = "rawMessageContent";
  public static final String SEQUENCE_NUMBER = "sequenceNumber";
  public static final String MESSAGE_TIME = "timestamp";
  public static final String MESSAGE_UUID = "uuid";
  public static final String TIGER_VERSION_KEY = "tigerVersion";

  private final AtomicBoolean versionHeaderWritten = new AtomicBoolean();
  private boolean writeVersionHeader = true;

  /**
   * Sets whether the version header should be written. Default is true. Set to false to skip
   * version header generation (e.g., for downloads).
   *
   * @param writeVersionHeader true to write version header, false to skip
   * @return this RbelFileWriter for method chaining
   */
  public RbelFileWriter setWriteVersionHeader(boolean writeVersionHeader) {
    this.writeVersionHeader = writeVersionHeader;
    return this;
  }

  public String convertToRbelFileString(RbelElement rbelElement) {
    return convertToRbelFileString(rbelElement, Long.MAX_VALUE);
  }

  @SneakyThrows
  public String convertToRbelFileString(RbelElement rbelElement, long skipContentThreshold) {
    final JSONObject jsonObject = new JSONObject(Map.of(MESSAGE_UUID, rbelElement.getUuid()));
    if (rbelElement.getSize() <= skipContentThreshold) {
      jsonObject.put(RAW_MESSAGE_CONTENT, encodeToBase64(rbelElement.getContent()));
    }
    rbelElement
        .getFacet(RbelMessageMetadata.class)
        .ifPresent(metadata -> metadata.forEach(jsonObject::put));

    if (writeVersionHeader && versionHeaderWritten.compareAndSet(false, true)) {
      if (!jsonObject.has(SEQUENCE_NUMBER)) {
        jsonObject.put(SEQUENCE_NUMBER, -1);
      }
      jsonObject.put(TIGER_VERSION_KEY, TigerVersionProvider.getTigerVersionString());
    }

    return jsonObject + FILE_DIVIDER;
  }

  @SneakyThrows
  private String encodeToBase64(RbelContent content) {
    try (var out = new ByteArrayOutputStream();
        var in = content.toInputStream()) {
      try (var encoding = Base64.getEncoder().wrap(out)) {
        in.transferTo(encoding);
      }
      return out.toString();
    }
  }
}
