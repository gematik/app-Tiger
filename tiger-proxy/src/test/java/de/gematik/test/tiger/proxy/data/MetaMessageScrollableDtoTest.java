/*
 * Copyright 2025 gematik GmbH
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

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.AbstractTigerProxyTest;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class MetaMessageScrollableDtoTest extends AbstractTigerProxyTest {

  @Test
  void checkMetaMessageScrollableDtoConversion() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    proxyRest.get("http://backend/foobar").asJson();
    awaitMessagesInTigerProxy(2);

    MetaMessageScrollableDto requestMetaData =
        MetaMessageScrollableDto.createFrom(tigerProxy.getRbelMessagesList().get(0));
    assertThat(requestMetaData)
        .hasFieldOrPropertyWithValue("infoString", "GET /foobar")
        .hasFieldOrPropertyWithValue("recipient", "backend:80")
        .hasFieldOrPropertyWithValue("sequenceNumber", 0L)
        .hasNoNullFieldsOrProperties();
    assertThat(requestMetaData.getSender()).matches("127\\.0\\.0\\.1:\\d*");

    MetaMessageScrollableDto responseMetaData =
        MetaMessageScrollableDto.createFrom(tigerProxy.getRbelMessagesList().get(1));
    assertThat(responseMetaData)
        .hasFieldOrPropertyWithValue("infoString", "666")
        .hasFieldOrPropertyWithValue("sender", "backend:80")
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

    MetaMessageScrollableDto requestMetaData =
        MetaMessageScrollableDto.createFrom(tigerProxy.getRbelMessagesList().get(0));
    assertThat(requestMetaData)
        .hasFieldOrPropertyWithValue(
            "infoString", "POST /1718790513675?_count=10&_offset=0&_total=none&_format=json")
        .hasFieldOrPropertyWithValue("recipient", "vau-proxy-server:8080")
        .hasFieldOrPropertyWithValue("sender", "192.168.128.1:44882")
        .hasFieldOrPropertyWithValue(
            "additionalInfoStrings",
            List.of(
                "GET /epa/medication/api/v1/fhir/Medication/Medication?_count=10&_offset=0&_total=none&_format=json"));

    MetaMessageScrollableDto responseMetaData =
        MetaMessageScrollableDto.createFrom(tigerProxy.getRbelMessagesList().get(1));
    assertThat(responseMetaData)
        .hasFieldOrPropertyWithValue("infoString", "200")
        .hasFieldOrPropertyWithValue("sender", "vau-proxy-server:8080")
        .hasFieldOrPropertyWithValue("recipient", "192.168.128.1:44882")
        .hasFieldOrPropertyWithValue("additionalInfoStrings", List.of("200"));
  }
}
