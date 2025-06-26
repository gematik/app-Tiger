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
package de.gematik.test.tiger.testenvmgr.utils;

import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RandomTestUtils {
  /**
   * Create a random generator with a random seed or with seed specified by the environment variable
   * "tiger.test.randomSeed".
   *
   * <p>Useful for reproducing unit tests which have a random component.
   *
   * @return a random generator
   */
  public static Random createRandomGenerator() {
    Random rand = new Random();
    long seed =
        Optional.ofNullable(System.getenv("tiger.test.randomSeed"))
            .map(Long::valueOf)
            .orElseGet(rand::nextLong);
    rand.setSeed(seed);
    log.info("🎲 random seed: " + seed);
    return rand;
  }
}
