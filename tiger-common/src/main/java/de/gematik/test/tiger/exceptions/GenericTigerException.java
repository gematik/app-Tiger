/*
 *
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
package de.gematik.test.tiger.exceptions;

import java.time.ZonedDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@EqualsAndHashCode(callSuper = true)
@Slf4j
public abstract class GenericTigerException extends RuntimeException {

  private final ZonedDateTime timestamp = ZonedDateTime.now();

  protected GenericTigerException(String message) {
    super(message);
  }

  protected GenericTigerException(String message, Throwable cause) {
    super(message, cause);
  }

  protected GenericTigerException(Throwable cause) {
    super(cause);
  }
}
