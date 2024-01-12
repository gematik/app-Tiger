package de.gematik.test.tiger.mockserver.model;

import java.util.concurrent.TimeUnit;

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
