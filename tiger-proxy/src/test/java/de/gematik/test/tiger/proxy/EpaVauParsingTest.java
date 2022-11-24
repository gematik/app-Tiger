/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class EpaVauParsingTest {

    @Test
    void shouldAddRecordIdFacetToAllHandshakeMessages() {
        try (var tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .sourceFile("src/test/resources/vauEpa2Flow.tgr")
                .build())
            .keyFolders(List.of("src/test/resources"))
            .activateVauAnalysis(true)
            .build())) {

            await()
                .atMost(20, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessagesList().size() >= 36);

            final String htmlData = RbelHtmlRenderer.render(tigerProxy.getRbelLogger().getMessageHistory());
            FileUtils.writeStringToFile(new File("target/vauFlow.html"), htmlData, StandardCharsets.UTF_8);

            assertThat(tigerProxy.getRbelMessagesList().get(24).findElement("$.body.recordId"))
                .get()
                .extracting(RbelElement::getRawStringContent)
                .isEqualTo("X114428539");
            assertThat(tigerProxy.getRbelMessagesList().get(25).findElement("$.body.recordId"))
                .get()
                .extracting(RbelElement::getRawStringContent)
                .isEqualTo("X114428539");

            assertThat(tigerProxy.getRbelMessagesList().get(28).findElement("$.body.recordId"))
                .get()
                .extracting(RbelElement::getRawStringContent)
                .isEqualTo("X114428539");
            assertThat(tigerProxy.getRbelMessagesList().get(29).findElement("$.body.recordId"))
                .get()
                .extracting(RbelElement::getRawStringContent)
                .isEqualTo("X114428539");

            assertThat(tigerProxy.getRbelMessagesList().get(30).findElement("$.body.recordId"))
                .get()
                .extracting(RbelElement::getRawStringContent)
                .isEqualTo("X114428539");
            assertThat(tigerProxy.getRbelMessagesList().get(31).findElement("$.body.recordId"))
                .get()
                .extracting(RbelElement::getRawStringContent)
                .isEqualTo("X114428539");

            assertThat(htmlData)
                .contains("P Header (raw):")
                .contains("01 00 00 00 00 00 00 00 07 00 00 01 07");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void verifyRiseTraffic() {
        try (var tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .fileSaveInfo(TigerFileSaveInfo.builder()
                .sourceFile("src/test/resources/rise-vau-log.tgr")
                .build())
            .activateVauAnalysis(true)
            .build())) {

            await()
                .atMost(20, TimeUnit.SECONDS)
                    .until(() -> tigerProxy.getRbelMessagesList().size() >= 16);

            assertThat(tigerProxy.getRbelMessagesList().get(15).findElement("$.body.recordId"))
                .get()
                .extracting(RbelElement::getRawStringContent)
                .isEqualTo("Y243631459");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
