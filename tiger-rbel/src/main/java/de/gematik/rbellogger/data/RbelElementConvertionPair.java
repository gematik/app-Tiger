/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.data;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * a wrapper class that holds a message, and optionally the corresponding paired request. It
 * simplifies the creation of the HttpResponseFacet while parsing, since we can directly set the
 * matching request.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class RbelElementConvertionPair {
  private final RbelElement message;
  private CompletableFuture<RbelElement> pairedRequest;

  public Optional<CompletableFuture<RbelElement>> getPairedRequest() {
    return Optional.ofNullable(pairedRequest);
  }
}