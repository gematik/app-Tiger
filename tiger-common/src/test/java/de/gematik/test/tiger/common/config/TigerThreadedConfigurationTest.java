/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;

class TigerThreadedConfigurationTest {

  @BeforeEach
  @AfterEach
  void setup() {
    TigerGlobalConfiguration.reset();
  }

  @Test
  void oneThreadSetsProperty_otherThreadsShouldNotSeeProperty() {
    final String myKey = "foobar";
    TigerGlobalConfiguration.putValue(myKey, "original Value");
    CompletableFuture<Void> changeValueFuture =
        CompletableFuture.runAsync(
            () -> {
              TigerGlobalConfiguration.putValue(myKey, "new Value", SourceType.THREAD_CONTEXT);
              assertEquals("new Value", TigerGlobalConfiguration.readString(myKey));
            });

    changeValueFuture.join();
    assertEquals("original Value", TigerGlobalConfiguration.readString(myKey));
  }

  @Test
  void tryToResetProperty_shouldWork() {
    final String myKey = "foobar";
    TigerGlobalConfiguration.putValue(myKey, "original Value");

    TigerGlobalConfiguration.putValue(myKey, "new Value 1", SourceType.THREAD_CONTEXT);
    assertThat(TigerGlobalConfiguration.readString(myKey))
      .isEqualTo("new Value 1");

    TigerGlobalConfiguration.putValue(myKey, "new Value 2", SourceType.THREAD_CONTEXT);
    assertThat(TigerGlobalConfiguration.readString(myKey))
      .isEqualTo("new Value 2");
  }

  @Test
  void mergeTwoThreadedSources() {
    var source1 = new TigerThreadScopedConfigurationSource();
    var source2 = new TigerThreadScopedConfigurationSource();
    source1.putValue(new TigerConfigurationKey("foo", "bar", "0"), "value0");
    source1.putValue(new TigerConfigurationKey("foo", "bar", "1"), "value1");
    source1.putValue(new TigerConfigurationKey("foo", "bar", "2"), "value2");

    source2.putValue(new TigerConfigurationKey("foo", "bar", "1"), "ANOTHER VALUE 1");
    source2.putValue(new TigerConfigurationKey("foo", "bar", "2"), "ANOTHER VALUE 2");
    source2.putValue(new TigerConfigurationKey("foo", "bar", "3"), "ANOTHER VALUE 3");

    source1.putAll(source2);
    assertThat(source1.getValues())
        .containsOnly(
            Pair.of(new TigerConfigurationKey("foo", "bar", "0"), "value0"),
            Pair.of(new TigerConfigurationKey("foo", "bar", "1"), "ANOTHER VALUE 1"),
            Pair.of(new TigerConfigurationKey("foo", "bar", "2"), "ANOTHER VALUE 2"),
            Pair.of(new TigerConfigurationKey("foo", "bar", "3"), "ANOTHER VALUE 3"));
  }
}
