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
package de.gematik.test.tiger.util;

import java.util.Random;
import java.util.UUID;
import lombok.val;

/**
 * Generates deterministic UUIDs given a previous UUID and a source position. This ensures
 * consistency in the UUIDs generated for the same source position across different tiger proxies.
 * (the parsed message will not always be transmitted!)
 */
public class DeterministicUuidGenerator {

  public static String generateUuid(String uuid, Integer positionInBaseNode) {
    val randomGenerator = new Random(uuid.hashCode() + (long) positionInBaseNode); // NOSONAR
    long mostSigBits = randomGenerator.nextLong();
    long leastSigBits = randomGenerator.nextLong();
    return new UUID(mostSigBits, leastSigBits).toString();
  }
}
