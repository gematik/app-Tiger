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
package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TigerConfigurationKeyWildcardsTest {

  @Test
  void testContainsKeyWithWildcards_exactMatch() {
    TigerConfigurationKey key = new TigerConfigurationKey("app.config.database.host");

    assertThat(key.containsKeyWithWildcards("app.config.database.host")).isTrue();
  }

  @Test
  void testContainsKeyWithWildcards_withSingleWildcard() {
    TigerConfigurationKey key = new TigerConfigurationKey("app.servers.backend.port");

    assertThat(key.containsKeyWithWildcards("app.servers.*.port")).isTrue();
  }

  @Test
  void testContainsKeyWithWildcards_withMultipleWildcards() {
    TigerConfigurationKey key = new TigerConfigurationKey("system.modules.auth.config.timeout");

    assertThat(key.containsKeyWithWildcards("system.*.*.config.timeout")).isTrue();
  }

  @Test
  void testContainsKeyWithWildcards_keyLongerThanPattern() {
    TigerConfigurationKey key = new TigerConfigurationKey("tiger.servers.proxy1.deprecated.routes.api.v1.endpoint");

    assertThat(key.containsKeyWithWildcards("tiger.servers.*.deprecated")).isTrue();
  }

  @Test
  void testContainsKeyWithWildcards_keyShorterThanPattern() {
    TigerConfigurationKey key = new TigerConfigurationKey("app.database.connection");

    assertThat(key.containsKeyWithWildcards("app.database.connection.pool.size")).isFalse();
  }

  @Test
  void testContainsKeyWithWildcards_noMatch() {
    TigerConfigurationKey key = new TigerConfigurationKey("service.cache.redis.host");

    assertThat(key.containsKeyWithWildcards("service.database.*.host")).isFalse();
  }

  @Test
  void testContainsKeyWithWildcards_caseInsensitive() {
    TigerConfigurationKey key = new TigerConfigurationKey("MyApp.MyService.MyProperty");

    assertThat(key.containsKeyWithWildcards("myapp.myservice.myproperty")).isTrue();
  }

  @Test
  void testContainsKeyWithWildcards_wildcardAtEnd() {
    TigerConfigurationKey key = new TigerConfigurationKey("logging.level.debug.enabled");

    assertThat(key.containsKeyWithWildcards("logging.level.debug.*")).isTrue();
  }

  @Test
  void testContainsKeyWithWildcards_wildcardAtBeginning() {
    TigerConfigurationKey key = new TigerConfigurationKey("production.servers.web.port");

    assertThat(key.containsKeyWithWildcards("*.servers.web.port")).isTrue();
  }

  @Test
  void testIsBelowUsingWildcards_directlyBelow() {
    TigerConfigurationKey key = new TigerConfigurationKey("database.connections.primary.maxpool");
    TigerConfigurationKey reference = new TigerConfigurationKey("database.connections.*");

    assertThat(key.isBelowUsingWildcards(reference)).isTrue();
  }

  @Test
  void testIsBelowUsingWildcards_multipleLevelsBelow() {
    TigerConfigurationKey key = new TigerConfigurationKey("api.endpoints.v2.users.create.validation.rules");
    TigerConfigurationKey reference = new TigerConfigurationKey("api.endpoints.*.users");

    assertThat(key.isBelowUsingWildcards(reference)).isTrue();
  }

  @Test
  void testIsBelowUsingWildcards_notBelow() {
    TigerConfigurationKey key = new TigerConfigurationKey("security.authentication.jwt");
    TigerConfigurationKey reference = new TigerConfigurationKey("security.authorization.*");

    assertThat(key.isBelowUsingWildcards(reference)).isFalse();
  }

  @Test
  void testIsBelowUsingWildcards_sameLevel() {
    TigerConfigurationKey key = new TigerConfigurationKey("cache.providers.redis.enabled");
    TigerConfigurationKey reference = new TigerConfigurationKey("cache.providers.*.enabled");

    assertThat(key.isBelowUsingWildcards(reference)).isFalse();
  }

  @Test
  void testIsBelowUsingWildcards_withMultipleWildcards() {
    TigerConfigurationKey key = new TigerConfigurationKey("monitoring.metrics.http.requests.total.count");
    TigerConfigurationKey reference = new TigerConfigurationKey("monitoring.*.*.requests");

    assertThat(key.isBelowUsingWildcards(reference)).isTrue();
  }

  @Test
  void testIsBelowUsingWildcards_noMatch() {
    TigerConfigurationKey key = new TigerConfigurationKey("storage.filesystem.local.path");
    TigerConfigurationKey reference = new TigerConfigurationKey("storage.cloud.*.path");

    assertThat(key.isBelowUsingWildcards(reference)).isFalse();
  }

  @Test
  void testCombinedUsage_deprecatedKeyDetection() {
    // Simuliert die Erkennung von deprecated Keys in Konfigurationsdateien
    TigerConfigurationKey deprecatedKey1 = new TigerConfigurationKey("app.legacy.oldconfig.connection.timeout");
    TigerConfigurationKey deprecatedKey2 = new TigerConfigurationKey("app.legacy.oldconfig.retries");
    TigerConfigurationKey validKey = new TigerConfigurationKey("app.config.newconfig.timeout");

    String deprecatedPattern = "app.legacy.oldconfig";

    // Deprecated Keys sollten matchen
    assertThat(deprecatedKey1.containsKeyWithWildcards(deprecatedPattern)).isTrue();
    assertThat(deprecatedKey2.containsKeyWithWildcards(deprecatedPattern)).isTrue();

    // Valider Key sollte nicht matchen
    assertThat(validKey.containsKeyWithWildcards(deprecatedPattern)).isFalse();
  }

  @Test
  void testCombinedUsage_serverConfiguration() {
    // Simuliert Server-Konfigurationen mit Wildcards
    TigerConfigurationKey server1Config = new TigerConfigurationKey("infrastructure.servers.web01.settings.port");
    TigerConfigurationKey server2Config = new TigerConfigurationKey("infrastructure.servers.api03.settings.host");
    TigerConfigurationKey otherConfig = new TigerConfigurationKey("infrastructure.network.gateway.address");

    String serverPattern = "infrastructure.servers.*.settings";

    // Server-Konfigurationen sollten matchen
    assertThat(server1Config.containsKeyWithWildcards(serverPattern)).isTrue();
    assertThat(server2Config.containsKeyWithWildcards(serverPattern)).isTrue();

    // Andere Konfiguration sollte nicht matchen
    assertThat(otherConfig.containsKeyWithWildcards(serverPattern)).isFalse();
  }
}
