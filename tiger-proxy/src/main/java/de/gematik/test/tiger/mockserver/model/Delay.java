/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.mockserver.model;

import java.util.concurrent.TimeUnit;

/*
 * @author jamesdbloom
 */
public class Delay extends ObjectWithReflectiveEqualsHashCodeToString {

  public static final Delay NONE = Delay.seconds(0);
  private final TimeUnit timeUnit;
  private final long value;

  public static Delay milliseconds(long value) {
    return new Delay(TimeUnit.MILLISECONDS, value);
  }

  public static Delay seconds(long value) {
    return new Delay(TimeUnit.SECONDS, value);
  }

  public static Delay minutes(long value) {
    return new Delay(TimeUnit.MINUTES, value);
  }

  public static Delay delay(TimeUnit timeUnit, long value) {
    return new Delay(timeUnit, value);
  }

  public Delay(TimeUnit timeUnit, long value) {
    this.timeUnit = timeUnit;
    this.value = value;
  }

  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  public long getValue() {
    return value;
  }

  public void applyDelay() {
    if (timeUnit != null) {
      try {
        timeUnit.sleep(value);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("InterruptedException while apply delay to response", ie);
      }
    }
  }
}
