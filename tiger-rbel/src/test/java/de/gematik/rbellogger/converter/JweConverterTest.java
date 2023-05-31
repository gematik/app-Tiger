/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelJweFacet;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class JweConverterTest {

    @Test
    @SneakyThrows
    void shouldConvertJwe() {
        final RbelLogger rbelConverter = RbelLogger.build(new RbelConfiguration()
            .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
            .addCapturer(RbelFileReaderCapturer.builder()
                .rbelFile("src/test/resources/ssoTokenFlow.tgr")
                .build()));
        rbelConverter.getRbelCapturer().initialize();

        final RbelElement postChallengeResponse = rbelConverter.getMessageList().stream()
            .filter(e -> e.hasFacet(RbelHttpRequestFacet.class))
            .filter(request -> request.getFacet(RbelHttpRequestFacet.class).get()
                .getPath().getRawStringContent().contains("/sign_response")
                && request.getFacet(RbelHttpRequestFacet.class).get().getMethod().getRawStringContent().equals("POST"))
            .findFirst().get();

        assertThat(postChallengeResponse)
            .extractChildWithPath("$..signed_challenge")
            .hasFacet(RbelJweFacet.class)
            .satisfies(
                el -> assertThat(el)
                    .extractChildWithPath("$.header")
                    .hasFacet(RbelJsonFacet.class),
                el -> assertThat(el)
                    .extractChildWithPath("$.body")
                    .hasFacet(RbelJwtFacet.class));
    }
}
