/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.capture;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;

@Slf4j
class PcapCaptureTest {

    private final static Map<String, String> MASKING_FUNCTIONS = new HashMap<>();

    {
        MASKING_FUNCTIONS.put("exp", "[Gültigkeit des Tokens. Beispiel: %s]");
        MASKING_FUNCTIONS
            .put("iat", "[Zeitpunkt der Ausstellung des Tokens. Beispiel: %s]");
        MASKING_FUNCTIONS
            .put("nbf",
                "[Der Token ist erst ab diesem Zeitpunkt gültig. Beispiel: %s]");

        MASKING_FUNCTIONS.put("code_challenge",
            "[code_challenge value, Base64URL(SHA256(code_verifier)). Beispiel: %s]");

        MASKING_FUNCTIONS.put("nonce",
            "[String value used to associate a Client session with an ID Token, and to mitigate replay attacks. Beispiel: %s]");

        MASKING_FUNCTIONS.put("state",
            "[OAuth 2.0 state value. Constant over complete flow. Value is a case-sensitive string. Beispiel: %s]");

        MASKING_FUNCTIONS.put("jti",
            "[A unique identifier for the token, which can be used to prevent reuse of the token. Value is a case-sensitive string. Beispiel: %s]");

        MASKING_FUNCTIONS.put("given_name",
            "[givenName aus dem subject-DN des authentication-Zertifikats. Beispiel: %s]");
        MASKING_FUNCTIONS.put("family_name",
            "[surname aus dem subject-DN des authentication-Zertifikats. Beispiel: %s]");
        MASKING_FUNCTIONS.put("idNummer",
            "[KVNR oder Telematik-ID aus dem authentication-Zertifikats. Beispiel: %s]");
        MASKING_FUNCTIONS.put("professionOID",
            "[professionOID des HBA aus dem authentication-Zertifikats. Null if not present. Beispiel: %s]");
        MASKING_FUNCTIONS.put("organizationName",
            "[professionOID des HBA  aus dem authentication-Zertifikats. Null if not present. Beispiel: %s]");
        MASKING_FUNCTIONS.put("auth_time",
            "[timestamp of authentication. Technically this is the time of authentication-token signing. Beispiel: %s]");
        MASKING_FUNCTIONS.put("snc",
            "[server-nonce. Used to introduce noise. Beispiel: %s]");
        MASKING_FUNCTIONS.put("sub",
            "[subject. Base64(sha256(audClaim + idNummerClaim + serverSubjectSalt)). Beispiel: %s]");
        MASKING_FUNCTIONS.put("at_hash",
            "[Erste 16 Bytes des Hash des Authentication Tokens Base64(subarray(Sha256(authentication_token), 0, 16)). Beispiel: %s]");

        MASKING_FUNCTIONS.put("authorization_endpoint", "[URL des Authorization Endpunkts.]");
        MASKING_FUNCTIONS.put("sso_endpoint", "[URL des Authorization Endpunkts.]");
        MASKING_FUNCTIONS.put("token_endpoint", "[URL des Authorization Endpunkts.]");
        MASKING_FUNCTIONS.put("uri_disc", "[URL des Discovery-Dokuments]");
        MASKING_FUNCTIONS.put("puk_uri_auth", "[URL einer JWK-Struktur des Authorization Public-Keys]");
        MASKING_FUNCTIONS.put("puk_uri_token", "[URL einer JWK-Struktur des Token Public-Keys]");
        MASKING_FUNCTIONS.put("jwks_uri", "[URL einer JWKS-Struktur mit allen vom Server verwendeten Schlüsseln]");
        MASKING_FUNCTIONS.put("njwt", "[enthält das Ursprüngliche Challenge Token des Authorization Endpunkt]");
        MASKING_FUNCTIONS.put("Date", "[Zeitpunkt der Antwort. Beispiel %s]");
    }

    @Test
    void pcapFile_checkMetadata() {
        final RbelFileReaderCapturer fileReaderCapturer = RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/discDoc.tgr")
            .build();
        final RbelLogger rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addCapturer(fileReaderCapturer));

        fileReaderCapturer.initialize();

        assertThat(rbelLogger.getMessageHistory().get(0)
            .getFacetOrFail(RbelTcpIpMessageFacet.class).getSenderHostname().toString())
            .hasToString("127.0.0.1:51441");
        assertThat(rbelLogger.getMessageHistory().get(0)
            .getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiverHostname().toString())
            .hasToString("127.0.0.1:8080");
    }

    @SneakyThrows
    @Test
    void readPcapFile_shouldParseMessages() {
        RbelOptions.activateJexlDebugging();
        final RbelFileReaderCapturer fileReaderCapturer = RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/deregisterPairing.tgr")
            .build();
        final RbelLogger rbelLogger = new RbelConfiguration()
            .addKey("IDP symmetricEncryptionKey",
                new SecretKeySpec(DigestUtils.sha256("geheimerSchluesselDerNochGehashtWird"), "AES"),
                RbelKey.PRECEDENCE_KEY_FOLDER)
            .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
            .addPostConversionListener(RbelKeyManager.RBEL_IDP_TOKEN_KEY_LISTENER)
            .addCapturer(fileReaderCapturer)
            .constructRbelLogger();

        MASKING_FUNCTIONS.forEach((k, v) -> rbelLogger.getValueShader().addSimpleShadingCriterion(k, v));
        rbelLogger.getValueShader().addJexlNoteCriterion(
            "message.url == '/auth/realms/idp/.well-known/openid-configuration' &&"
                + "message.method == 'GET' && message.request == true && 'RbelHttpRequestFacet' =~ facets",
            "Discovery Document anfragen");
        rbelLogger.getValueShader().addJexlNoteCriterion(
            "request.url == '/auth/realms/idp/.well-known/openid-configuration' &&"
                + "request.method == 'GET' && message.response && 'RbelHttpResponseFacet' =~ facets",
            "Discovery Document Response");
        rbelLogger.getValueShader().addJexlNoteCriterion("message.url =^ '/sign_response?' "
            + "&& message.method=='GET' && key == 'scope'", "scope Note!!");
        rbelLogger.getValueShader().addJexlNoteCriterion("path == 'body.key_verifier.body'",
            "key verifier body note");
        rbelLogger.getValueShader().addJexlNoteCriterion("key == 'code_verifier'",
            "the long forgotten code verifier");
        rbelLogger.getValueShader().addJexlNoteCriterion("path =$ 'x5c.0'",
            "some note about x5c");
        rbelLogger.getValueShader().addJexlNoteCriterion("key == 'pairing_endpoint'",
            "Hier gibts die pairings");
        rbelLogger.getValueShader().addJexlNoteCriterion("key == 'user_consent'",
            "Note an einem Object");

        fileReaderCapturer.initialize();

        addRandomTimestamps(rbelLogger);

        log.info("start rendering " + LocalDateTime.now());
        final String render = new RbelHtmlRenderer()
            .doRender(rbelLogger.getMessageHistory());
        FileUtils.writeStringToFile(new File("target/pairingList.html"),
            render, Charset.defaultCharset());
        log.info("completed rendering " + LocalDateTime.now());

        assertThat(rbelLogger.getMessageHistory().get(0).hasFacet(RbelHttpRequestFacet.class))
            .isTrue();
        assertThat(rbelLogger.getMessageHistory().get(1).hasFacet(RbelHttpResponseFacet.class))
            .isTrue();
        assertThat(rbelLogger.getMessageHistory().get(0).getNotes())
            .extracting("value")
            .containsExactly("Discovery Document anfragen");
        assertThat(rbelLogger.getMessageHistory().get(1).getNotes())
            .extracting("value")
            .containsExactly("Discovery Document Response");
        assertThat(render).contains("Hier gibts die pairings")
            .contains("some note about x5c")
            .contains("Note an einem Object");
    }

    private void addRandomTimestamps(RbelLogger rbelLogger) {
        ZonedDateTime now = ZonedDateTime.now();
        for (RbelElement msg : rbelLogger.getMessageHistory()) {
            msg.addFacet(RbelMessageTimingFacet.builder()
                .transmissionTime(now)
                .build());
            now = now.plusNanos((long) (1000 * 1000 * RandomUtils.nextDouble(10, 1000)));
        }
    }
}
