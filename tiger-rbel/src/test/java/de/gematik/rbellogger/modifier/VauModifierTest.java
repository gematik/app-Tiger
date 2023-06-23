/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.rbellogger.modifier;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelErpVauDecrpytionConverter;
import de.gematik.rbellogger.converter.RbelVauEpaConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.Security;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VauModifierTest {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private RbelLogger rbelLogger;

    @BeforeEach
    public void initRbelLogger() {
        if (rbelLogger == null) {
            rbelLogger = RbelLogger.build(
                new RbelConfiguration()
                    .addAdditionalConverter(new RbelVauEpaConverter())
                    .addAdditionalConverter(new RbelErpVauDecrpytionConverter())
                    .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
        }
        rbelLogger.getRbelModifier().deleteAllModifications();
    }

    @Test
    public void modifyErpVauRequestBody() throws IOException {
        final RbelElement message = readAndConvertRawMessage("src/test/resources/vauErpRequest.b64");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.message.body")
            .replaceWith("<New>Vau inner body</New>")
            .build());

        final RbelElement modifiedMessage = rbelLogger.getRbelModifier().applyModifications(message);

        assertThat(modifiedMessage.findElement("$.body.message.body.New.text")
            .map(RbelElement::getRawStringContent).get())
            .isEqualTo("Vau inner body");
    }

    @Test
    public void modifyErpVauResponseBody() throws IOException {
        rbelLogger.getRbelKeyManager().addKey("secretKey",
            new SecretKeySpec(Base64.getDecoder().decode("dGPgkcT15xeXhORNsgc83A=="), "AES"), 0);
        final RbelElement message = readAndConvertRawMessage("src/test/resources/vauErpResponse.b64");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.message.body")
            .replaceWith("<New>Vau inner body</New>")
            .build());

        final RbelElement modifiedMessage = rbelLogger.getRbelModifier().applyModifications(message);
        assertThat(modifiedMessage.findElement("$.body.message.body.New.text")
            .map(RbelElement::getRawStringContent).get())
            .isEqualTo("Vau inner body");
    }

    @Test
    public void modifyEpaVauRequestBody() {
        rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
            .addAdditionalConverter(new RbelVauEpaConverter())
            .addCapturer(RbelFileReaderCapturer.builder()
                .rbelFile("src/test/resources/vauFlow.tgr")
                .build())
        );
        rbelLogger.getRbelCapturer().initialize();
        final RbelElement message = rbelLogger.getMessageList().get(4);

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.message")
            .replaceWith("<New>Vau inner body</New>")
            .build());

        final RbelElement modifiedMessage = rbelLogger.getRbelModifier().applyModifications(message);

        assertThat(modifiedMessage.findElement("$.body.message.New.text")
            .map(RbelElement::getRawStringContent))
            .get()
            .isEqualTo("Vau inner body");
    }

    private RbelElement readAndConvertRawMessage(String fileName) throws IOException {
        String rawMessage = FileUtils.readFileToString(new File(fileName), Charset.defaultCharset());
        return rbelLogger.getRbelConverter()
            .convertElement(Base64.getDecoder().decode(rawMessage), null);
    }
}
