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

package de.gematik.test.tiger.proxy.data;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.renderer.MessageMetaDataDto;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.AbstractTigerProxyTest;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestMessageMetaDataDto extends AbstractTigerProxyTest {

  @Test
  void checkMessageMetaDataDtoConversion() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.get("http://backend/foobar").asJson();
    awaitMessagesInTigerProxy(2);

    MessageMetaDataDto requestMetaData =
        MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(0));
    assertThat(requestMetaData)
        .hasFieldOrPropertyWithValue("menuInfoString", "GET /foobar")
        .hasFieldOrPropertyWithValue("recipient", "backend:80")
        .hasFieldOrPropertyWithValue("symbol", "fa-share")
        .hasFieldOrPropertyWithValue("color", "has-text-link")
        .hasFieldOrPropertyWithValue("sequenceNumber", 0L);
    assertThat(requestMetaData.getSender()).matches("127\\.0\\.0\\.1:\\d*");

    MessageMetaDataDto responseMetaData =
        MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(1));
    assertThat(responseMetaData)
        .hasFieldOrPropertyWithValue("menuInfoString", "666")
        .hasFieldOrPropertyWithValue("sender", "backend:80")
        .hasFieldOrPropertyWithValue("symbol", "fa-reply")
        .hasFieldOrPropertyWithValue("color", "text-success")
        .hasFieldOrPropertyWithValue("sequenceNumber", 1L);
    assertThat(responseMetaData.getRecipient()).matches("127\\.0\\.0\\.1:\\d*");
  }

  @Test
  void checkVau3MetaDataDtoConversion() {
    tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .fileSaveInfo(
                    TigerFileSaveInfo.builder()
                        .sourceFile("../tiger-rbel/src/test/resources/vau3WithInnerGzip.tgr")
                        .build())
                .activateRbelParsingFor(List.of("epa3-vau"))
                .build());

    awaitMessagesInTigerProxy(2);

    MessageMetaDataDto requestMetaData =
        MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(0));
    assertThat(requestMetaData)
        .hasFieldOrPropertyWithValue(
            "menuInfoString", "POST /1718790513675?_count=10&_offset=0&_total=none&_format=json")
        .hasFieldOrPropertyWithValue("recipient", "vau-proxy-server:8080")
        .hasFieldOrPropertyWithValue("sender", "192.168.128.1:44882")
        .hasFieldOrPropertyWithValue(
            "additionalInformation",
            List.of(
                "GET /epa/medication/api/v1/fhir/Medication/Medication?_count=10&_offset=0&_total=none&_format=json"));

    MessageMetaDataDto responseMetaData =
        MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(1));
    assertThat(responseMetaData)
        .hasFieldOrPropertyWithValue("menuInfoString", "200")
        .hasFieldOrPropertyWithValue("sender", "vau-proxy-server:8080")
        .hasFieldOrPropertyWithValue("recipient", "192.168.128.1:44882")
        .hasFieldOrPropertyWithValue("additionalInformation", List.of("200"));
  }
}
