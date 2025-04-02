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

package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import kong.unirest.core.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
@SuppressWarnings("java:S5778")
class TestTigerProxyInfiniteLoops extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  @Disabled("TGR-1792")
  void simpleInfiniteLoop_shouldGiveError() {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/")
            .to("http://localhost:" + tigerProxy.getProxyPort() + "/foo")
            .build());

    assertThat(tigerProxy.getRbelMessagesList()).isEmpty();

    assertThatThrownBy(
            () -> unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort()).asString())
        .isInstanceOf(UnirestException.class);

    awaitMessagesInTiger(23);

    final String htmlData = RbelHtmlRenderer.render(tigerProxy.getRbelLogger().getMessageHistory());
    FileUtils.writeStringToFile(new File("target/error.html"), htmlData, StandardCharsets.UTF_8);
    assertThat(tigerProxy.getRbelMessagesList().get(11))
        .extractChildWithPath("$.error.message")
        .asString()
        .startsWith("Infinite loop detected for /127.0.0.1:");
    assertThat(tigerProxy.getRbelMessagesList()).hasSize(23);
  }

  @Test
  void trickyNonInfiniteLoop_shouldPass() {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/foo")
            .to("http://localhost:" + tigerProxy.getProxyPort() + "/bar")
            .build());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/")
            .to("http://localhost:" + tigerProxy.getProxyPort() + "/foo")
            .build());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/bar")
            .to("http://localhost:" + fakeBackendServerPort)
            .build());

    assertThatNoException()
        .isThrownBy(
            () -> unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort()).asString());
  }

  @Test
  void trickyInfiniteLoop_shouldGiveError() {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/")
            .to("http://localhost:" + tigerProxy.getProxyPort() + "/foo")
            .build());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/foo")
            .to("http://localhost:" + tigerProxy.getProxyPort() + "/bar")
            .build());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/bar")
            .to("http://localhost:" + tigerProxy.getProxyPort() + "/foo")
            .build());

    assertThatThrownBy(
            () -> unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort()).asString())
        .isInstanceOf(UnirestException.class);
  }
}
