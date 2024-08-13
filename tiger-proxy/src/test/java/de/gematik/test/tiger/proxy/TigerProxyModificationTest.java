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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import java.util.List;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
@ResetTigerConfiguration
class TigerProxyModificationTest {

  @Autowired private TigerProxy tigerProxy;
  private TigerRemoteProxyClient tigerRemoteProxyClient;
  @LocalServerPort private int managementPort;
  private UnirestInstance unirestInstance;

  @BeforeEach
  public void beforeEachLifecyleMethod() {
    tigerProxy.getModifications().stream()
        .map(RbelModificationDescription::getName)
        .forEach(tigerProxy::removeModification);

    tigerRemoteProxyClient =
        new TigerRemoteProxyClient(
            "http://localhost:" + managementPort, TigerProxyConfiguration.builder().build());

    if (unirestInstance != null) {
      return;
    }

    unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().defaultBaseUrl("http://localhost:" + managementPort);
  }

  @AfterEach
  public void reset() {
    tigerRemoteProxyClient.close();
    tigerRemoteProxyClient.getRbelLogger().getRbelModifier().deleteAllModifications();
    tigerProxy.getRbelLogger().getRbelModifier().deleteAllModifications();
  }

  @Test
  void addModification_shouldWork() {
    final RbelModificationDescription modificationDescription =
        RbelModificationDescription.builder()
            .condition("isRequest")
            .targetElement("$.header.user-agent")
            .replaceWith("modified user-agent")
            .build();
    tigerRemoteProxyClient.addModificaton(modificationDescription);

    final RbelModificationDescription modification =
        tigerProxy.getRbelLogger().getRbelModifier().getModifications().get(0);

    assertThat(modification.getCondition()).isEqualTo("isRequest");
    assertThat(modification.getTargetElement()).isEqualTo("$.header.user-agent");
    assertThat(modification.getReplaceWith()).isEqualTo("modified user-agent");
  }

  @Test
  void getModifications_shouldGiveAllModifications() {
    final RbelModificationDescription modification1 =
        RbelModificationDescription.builder().name("test1").build();
    final RbelModificationDescription modification2 =
        RbelModificationDescription.builder().name("test2").build();

    tigerRemoteProxyClient.addModificaton(modification1);
    tigerRemoteProxyClient.addModificaton(modification2);

    final List<RbelModificationDescription> modifications =
        tigerRemoteProxyClient.getModifications();

    assertThat(modifications)
        .extracting(RbelModificationDescription::getName)
        .contains("test1", "test2");
  }

  @Test
  void deleteModification_shouldWork() {
    final RbelModificationDescription modificationDescription =
        RbelModificationDescription.builder()
            .condition("isRequest")
            .targetElement("$.header.user-agent")
            .replaceWith("modified user-agent")
            .name("blub")
            .build();
    final String modificationName =
        tigerRemoteProxyClient.addModificaton(modificationDescription).getName();

    assertThat(modificationName).isEqualTo("blub");

    tigerRemoteProxyClient.removeModification(modificationName);

    assertThat(tigerProxy.getRbelLogger().getRbelModifier().getModifications()).isEmpty();
  }

  @Test
  void deleteModificationWithoutName_shouldWork() {
    final RbelModificationDescription modificationDescription =
        RbelModificationDescription.builder()
            .condition("isRequest")
            .targetElement("$.header.user-agent")
            .replaceWith("modified user-agent")
            .build();
    final String modificationName =
        tigerRemoteProxyClient.addModificaton(modificationDescription).getName();

    assertThat(modificationName).isNotNull();

    tigerRemoteProxyClient.removeModification(modificationName);

    assertThat(tigerProxy.getRbelLogger().getRbelModifier().getModifications()).isEmpty();
  }
}
