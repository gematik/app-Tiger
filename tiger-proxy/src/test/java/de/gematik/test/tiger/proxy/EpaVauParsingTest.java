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
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "tigerProxy.activateVauAnalysis = true",
        "tigerProxy.keyFolders.0 = src/test/resources"
    })
@RequiredArgsConstructor
@TestConfiguration()
public class EpaVauParsingTest {

    @Autowired
    private TigerProxy tigerProxy;

    @Test
    public void shouldAddRecordIdFacetToAllHandshakeMessages() throws IOException {
        String rawSavedVauMessages = FileUtils.readFileToString(new File("src/test/resources/vauEpa2Flow.rawHttpDump"));
        Stream.of(rawSavedVauMessages.split("\n\n"))
            .map(Base64.getDecoder()::decode)
            .forEach(msgBytes -> tigerProxy.getRbelLogger()
                .getRbelConverter().parseMessage(msgBytes, null, null));

        FileUtils.writeStringToFile(new File("target/vauFlow.html"),
            RbelHtmlRenderer.render(tigerProxy.getRbelLogger().getMessageHistory()));

        assertThat(tigerProxy.getRbelMessages().get(24).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
        assertThat(tigerProxy.getRbelMessages().get(25).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");

        assertThat(tigerProxy.getRbelMessages().get(28).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
        assertThat(tigerProxy.getRbelMessages().get(29).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");

        assertThat(tigerProxy.getRbelMessages().get(30).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
        assertThat(tigerProxy.getRbelMessages().get(31).findElement("$.body.recordId"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("X114428539");
    }
}
