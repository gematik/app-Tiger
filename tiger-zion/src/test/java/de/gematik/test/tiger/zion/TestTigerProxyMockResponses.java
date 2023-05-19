package de.gematik.test.tiger.zion;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.zion.config.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kong.unirest.Empty;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
@TestPropertySource(properties = {
    "zion.mockResponseFiles.firstFile=src/test/resources/someMockResponse.yaml"})
class TestTigerProxyMockResponses {

    final Path tempDirectory = Path.of("target", "zionResponses");

    @Autowired
    private ZionConfiguration configuration;
    @Autowired
    private ObjectMapper objectMapper;
    @LocalServerPort
    private int port;
    private Map<String, TigerMockResponse> mockResponsesBackup;

    @SneakyThrows
    @BeforeEach
    public void setupTempDirectory() {
        Files.createDirectories(tempDirectory);
        Files.list(tempDirectory).forEach(path -> path.toFile().delete());
        mockResponsesBackup = configuration.getMockResponses();
    }

    @AfterEach
    public void resetMockResponses() {
        configuration.setMockResponses(mockResponsesBackup);
        configuration.setSpy(null);
    }

    @Test
    void simpleMockedResponse() {
        configuration.setMockResponses(Map.of("backend_foobar",
            TigerMockResponse.builder()
                .requestCriterions(List.of(
                    "message.method == 'GET'",
                    "message.url =~ '.*/userJsonPath.*'"))
                .response(TigerMockResponseDescription.builder()
                    .statusCode(666)
                    .body("{\n"
                        + "  \"authorizedUser\": \"!{$.path.username.value}\",\n"
                        + "  \"someCertificate\": \"!{keyMgr.b64Certificate('puk_idp_enc')}\"\n"
                        + "}\n")
                    .build())
                .build()));

        final HttpResponse<JsonNode> response = Unirest.get("http://localhost:" + port
            + "/userJsonPath?username=someUsername").asJson();
        assertThat(response.getStatus())
            .isEqualTo(666);
        assertThat(response.getBody().getObject().getString("authorizedUser"))
            .isEqualTo("someUsername");
    }

    @Test
    void testMockResponseFromFile() {
        final HttpResponse<JsonNode> response = Unirest.post("http://localhost:" + port + "/specificEndpoint")
            .body("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6Im15SGFwcHlMaXR0bGVGb29iYXIifQ.u0NTCYczr5qVmwgvU21GgecaibDwnn6voWmvmFPvPh8")
            .asJson();
        assertThat(response.getStatus())
            .isEqualTo(203);
        assertThat(response.getBody().getObject().getString("authorizedUser"))
            .isEqualTo("myHappyLittleFoobar");
    }

    @Test
    void testSpyFunctionality() throws IOException {
        try (MockServer mockServer = new MockServer();
            MockServerClient mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort())) {
            mockServerClient.when(request()
                    .withMethod("GET")
                    .withPath(".*"))
                .respond(response().withStatusCode(666).withBody("{\"foo\":\"bar\"}"));

            configuration.setSpy(ZionSpyConfiguration.builder()
                .url("http://localhost:" + mockServer.getLocalPort() + "/deepPath")
                .protocolToPath("target/zionResponses")
                .build());

            Unirest.get("http://localhost:" + port + "/shallowPath?foo=bar").asJson();

            final TigerMockResponse mockResponse = objectMapper.readValue(
                Files.list(Path.of("target", "zionResponses"))
                    .findAny().get().toFile(),
                TigerMockResponse.class);

            assertThat(mockResponse.getRequestCriterions())
                .contains("message.method == 'GET'")
                .contains("message.url =$ '/shallowPath?foo=bar'");
        }
    }

    @Test
    void testTemplatedBackendRequest() {
        try (MockServer mockServer = new MockServer();
            MockServerClient mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort())) {
            mockServerClient.when(request())
                .respond(req -> response().withStatusCode(200)
                    .withBody(req.getBodyAsString()));

            configuration.setMockResponses(Map.of(
                "templatedBackendRequest",
                TigerMockResponse.builder()
                    .backendRequests(Map.of("theRequest",
                        ZionBackendRequestDescription.builder()
                            .url("http://localhost:" + mockServer.getLocalPort() + "/deepPath")
                            .body("{\n"
                                + "  \"tgrEncodeAs\":\"JWT\",\n"
                                + "  \"header\":{\n"
                                + "    \"alg\": \"BP256R1\",\n"
                                + "    \"typ\": \"JWT\"\n"
                                + "  },\n"
                                + "  \"body\":{\n"
                                + "    \"sub\": \"1234567890\",\n"
                                + "    \"name\": \"John Doe\",\n"
                                + "    \"iat\": 1516239022\n"
                                + "  },\n"
                                + "  \"signature\":{\n"
                                + "    \"verifiedUsing\":\"idp_enc\"\n"
                                + "  }\n"
                                + "}")
                            .assignments(Map.of("signer", "$.body.signature.verifiedUsing"))
                            .build()))
                    .response(TigerMockResponseDescription.builder()
                        .body("${signer}")
                        .build())
                    .build()
            ));

            assertThat(Unirest.get("http://localhost:" + port + "/shallowPath?foo=bar")
                .asString()
                .getBody())
                .isEqualTo("puk_idp_enc");
        }
    }

    @Test
    void testRbelPathCriterions() {
        configuration.setMockResponses(Map.of("backend_foobar",
            TigerMockResponse.builder()
                .requestCriterions(List.of(
                    "$.path.username.value=='someUsername'"))
                .response(TigerMockResponseDescription.builder()
                    .statusCode(666)
                    .build())
                .build()));

        final HttpResponse<Empty> response = Unirest.get("http://localhost:" + port
            + "/userJsonPath?username=someUsername").asEmpty();
        assertThat(response.getStatus())
            .isEqualTo(666);
    }

    @Test
    void testConfigurationAssignments() {
        configuration.setMockResponses(Map.of("backend_foobar",
            TigerMockResponse.builder()
                .requestCriterions(List.of(
                    "message.method == 'GET'"))
                .assignments(Map.of("foo.bar.variable", "$.path.username.value"))
                .response(TigerMockResponseDescription.builder()
                    .statusCode(666)
                    .body("{\"authorizedUser\": \"${foo.bar.variable}\"}\n")
                    .build())
                .build()));

        final HttpResponse<JsonNode> response = Unirest.get("http://localhost:" + port
            + "/userJsonPath?username=someUsername").asJson();
        assertThat(response.getBody().getObject().getString("authorizedUser"))
            .isEqualTo("someUsername");
    }
/*
    @Test
    void testIdpClient() {
        configuration.setMockResponses(Map.of(
            "discovery_document", discoveryDocumentMockResponse(),
            "key_endpoints", keyEndpoints(),
            "sign_response", signResponse(),
            "sign_response_post", signResponsePost(),
            "token", tokenEndpoint()));

        final IdpClient idpClient = IdpClient.builder()
            .clientId("eRezeptApp")
            .discoveryDocumentUrl("http://localhost:" + port + IdpConstants.DISCOVERY_DOCUMENT_ENDPOINT)
            .redirectUrl("http://redirect.gematik.de/erezept")
            .build();
        final TigerPkiIdentity clientIdentity = new TigerPkiIdentity("src/test/resources/egk_identity.p12;00");

        idpClient.initialize();
        idpClient.login(PkiIdentity.builder()
            .certificate(clientIdentity.getCertificate())
            .privateKey(clientIdentity.getPrivateKey())
            .build());
    }*/


    @Test
    void testSpyFunctionalityWithJwt() throws IOException {
        try (MockServer mockServer = new MockServer();
            MockServerClient mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort())) {
            mockServerClient.when(request()
                    .withMethod("GET")
                    .withPath(".*"))
                .respond(response().withStatusCode(666).withBody(
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"));

            configuration.setSpy(ZionSpyConfiguration.builder()
                .url("http://localhost:" + mockServer.getLocalPort() + "/deepPath")
                .protocolToPath("target/zionResponses")
                .build());

            Unirest.get("http://localhost:" + port + "/shallowPath?foo=bar").asJson();

            final TigerMockResponse mockResponse = objectMapper.readValue(
                Files.list(tempDirectory)
                    .findAny().get().toFile(),
                TigerMockResponse.class);

            assertThat(mockResponse.getResponse().getBody())
                .containsIgnoringWhitespaces("{\n"
                    + "  \"sub\": \"1234567890\",\n"
                    + "  \"name\": \"John Doe\",\n"
                    + "  \"iat\": 1516239022\n"
                    + "}");
        }
    }

    private TigerMockResponse tokenEndpoint() {
        return TigerMockResponse.builder()
            .requestCriterions(List.of(
                "message.method == 'POST'",
                "message.url =~ '.*/token'"))
            .response(TigerMockResponseDescription.builder()
                .bodyFile("src/test/resources/idpTokenEndpointResponseBody.json")
                .build())
            .build();
    }

    private TigerMockResponse signResponsePost() {
        return TigerMockResponse.builder()
            .requestCriterions(List.of(
                "message.method == 'POST'",
                "message.url =~ '.*/sign_response?.*'"))
            .response(TigerMockResponseDescription.builder()
                .statusCode(302)
                .headers(Map.of("Location", "{\n"
                    + "  \"tgrEncodeAs\": \"url\",\n"
                    + "  \"basicPath\": \"http://redirect.gematik.de/erezept\",\n"
                    + "  \"parameters\": {\n"
                    + "    \"state\": \"?{$..body.state.content}\",\n"
                    + "    \"code\": {\n"
                    + "      \"tgrEncodeAs\": \"JWE\",\n"
                    + "      \"header\": {\n"
                    + "        \"alg\": \"ECDH-ES\",\n"
                    + "        \"enc\": \"A256GCM\",\n"
                    + "        \"cty\": \"NJWT\"\n"
                    + "      },\n"
                    + "      \"body\": {\n"
                    + "        \"njwt\": {\n"
                    + "          \"tgrEncodeAs\": \"JWT\",\n"
                    + "          \"header\": {\n"
                    + "            \"alg\": \"BP256R1\",\n"
                    + "            \"typ\": \"JWT\",\n"
                    + "            \"kid\": \"puk_idp_sig\"\n"
                    + "          },\n"
                    + "          \"body\": {\n"
                    + "            \"organizationName\": \"Test GKV-SVNOT-VALID\",\n"
                    + "            \"professionOID\": \"?{$..7..[?(@._0 == '1.3.36.8.3.3')].1..1.0}\",\n"
                    + "            \"idNummer\": \"?{$..x5c.0.content.subject.[?(key=='OU' && content =^ 'X')]}\",\n"
                    + "            \"amr\": [\"mfa\",\"sc\",\"pin\"],\n"
                    + "            \"iss\": \"http://url.des.idp\",\n"
                    + "            \"response_type\": \"code\",\n"
                    + "            \"snc\": \"BC8n0vKfxTiFZGdLtZzf\",\n"
                    + "            \"code_challenge_method\": \"S256\",\n"
                    + "            \"given_name\": \"?{$..x5c.0.content.subject.GN}\",\n"
                    + "            \"token_type\": \"code\",\n"
                    + "            \"nonce\": \"?{$..nonce.content}\",\n"
                    + "            \"client_id\": \"?{$..client_id.content}\",\n"
                    + "            \"scope\": \"?{$..scope.content}\",\n"
                    + "            \"auth_time\": 1667404477,\n"
                    + "            \"redirect_uri\": \"?{$..redirect_uri.content}\",\n"
                    + "            \"state\": \"?{$..body.state.content}\",\n"
                    + "            \"exp\": 1667404537,\n"
                    + "            \"family_name\": \"?{$..x5c.0.content.subject.SURNAME}\",\n"
                    + "            \"iat\": 1667404477,\n"
                    + "            \"code_challenge\": \"?{$..code_challenge.content}\",\n"
                    + "            \"jti\": \"?{$..jti.content}\"\n"
                    + "          },\n"
                    + "          \"signature\": {\n"
                    + "            \"verifiedUsing\": \"idp_sig\"\n"
                    + "          }\n"
                    + "        }\n"
                    + "      },\n"
                    + "      \"encryptionInfo\": {\n"
                    + "        \"decryptedUsingKeyWithId\": \"puk_idp_enc\"\n"
                    + "      }\n"
                    + "    }\n"
                    + "  }\n"
                    + "}"))
                .build())
            .build();
    }

    private TigerMockResponse signResponse() {
        return TigerMockResponse.builder()
            .requestCriterions(List.of(
                "message.method == 'GET'",
                "message.url =~ '.*/sign_response?.*'"))
            .response(TigerMockResponseDescription.builder()
                .body("{\n"
                    + "  \"challenge\": {\n"
                    + "      \"tgrEncodeAs\": \"JWT\",\n"
                    + "      \"header\": {\n"
                    + "        \"alg\": \"BP256R1\",\n"
                    + "        \"typ\": \"JWT\",\n"
                    + "        \"kid\": \"puk_idp_sig\"\n"
                    + "      },\n"
                    + "      \"body\": {\n"
                    + "        \"iss\": \"http://url.des.idp\",\n"
                    + "        \"response_type\": \"code\",\n"
                    + "        \"snc\": \"uJdv7zlE6Fh1I0Xl6MSBFA-VUgD3nL7U6i2W2P2pUrA\",\n"
                    + "        \"code_challenge_method\": \"S256\",\n"
                    + "        \"token_type\": \"challenge\",\n"
                    + "        \"nonce\": \"?{$.path.nonce.value}\",\n"
                    + "        \"client_id\": \"?{$.path.client_id.value}\",\n"
                    + "        \"scope\": \"openid e-rezept\",\n"
                    + "        \"state\": \"dU9TWgLIoSTLIEBXgftr\",\n"
                    + "        \"redirect_uri\": \"http://redirect.gematik.de/erezept\",\n"
                    + "        \"exp\": 1667404656,\n"
                    + "        \"iat\": 1667404476,\n"
                    + "        \"code_challenge\": \"ztagmrO3wT6_3eNn9jRJO_exBvxn8xj1SKRPNN1wuf0\",\n"
                    + "        \"jti\": \"03d29fb8634fdfa2\"\n"
                    + "      },\n"
                    + "      \"signature\": {\n"
                    + "        \"verifiedUsing\": \"idp_sig\"\n"
                    + "      }\n"
                    + "    "
                    + "  }\n"
                    + "}")
                .build())
            .build();
    }

    private TigerMockResponse keyEndpoints() {
        return TigerMockResponse.builder()
            .requestCriterions(List.of(
                "message.method == 'GET'",
                "message.url =~ '.*jwk.json?.*'"))
            .response(TigerMockResponseDescription.builder()
                .body("{\n"
                    + "  \"x5c\": [\n"
                    + "    \"!{keyMgr.b64Certificate('puk_?{$.path.keyId.value}')}\"\n"
                    + "  ],\n"
                    + "  \"use\": \"sig\",\n"
                    + "  \"kid\": \"puk_idp_sig\",\n"
                    + "  \"kty\": \"EC\",\n"
                    + "  \"crv\": \"BP-256\",\n"
                    + "  \"x\": \"llCsbU1bEgHeTP_pnbOiOWQmo3e8ldncRmcnoldNfDk\",\n"
                    + "  \"y\": \"ZDFZ5XjwWmtgfomv3VOV7qzI5ycUSJysMWDEu3mqRcY\"\n"
                    + "}")
                .build())
            .build();
    }

    private static TigerMockResponse discoveryDocumentMockResponse() {
        return TigerMockResponse.builder()
            .requestCriterions(List.of(
                "message.method == 'GET'",
                "message.url =$ '/.well-known/openid-configuration'"))
            .response(TigerMockResponseDescription.builder()
                .body("{\n"
                    + "  \"tgrEncodeAs\": \"JWT\",\n"
                    + "  \"header\": {\n"
                    + "    \"alg\": \"BP256R1\",\n"
                    + "    \"typ\": \"JWT\",\n"
                    + "    \"kid\": \"puk_disc_sig\",\n"
                    + "    \"x5c\": [\"!{keyMgr.b64Certificate('puk_disc_sig')"
                    + "}\"]\n"
                    + "  },\n"
                    + "  \"body\": {\n"
                    + "    \"authorization_endpoint\": \"http://localhost:${zion.port}/sign_response\",\n"
                    + "    \"auth_pair_endpoint\": \"http://localhost:${zion.port}/alt_response\",\n"
                    + "    \"sso_endpoint\": \"http://localhost:${zion.port}/sso_response\",\n"
                    + "    \"uri_pair\": \"http://localhost:${zion.port}/pairings\",\n"
                    + "    \"token_endpoint\": \"http://localhost:${zion.port}/token\",\n"
                    + "    \"third_party_authorization_endpoint\": \"http://localhost:${zion.port}/extauth\",\n"
                    + "    \"uri_disc\": \"http://localhost:${zion.port}/.well-known/openid-configuration\",\n"
                    + "    \"issuer\": \"https://idp.zentral.idp.splitdns.ti-dienste.de\",\n"
                    + "    \"jwks_uri\": \"http://localhost:${zion.port}/jwks\",\n"
                    + "    \"exp\": 1667490873,\n"
                    + "    \"iat\": 1667404473,\n"
                    + "    \"uri_puk_idp_enc\": \"http://localhost:${zion.port}/jwk.json?keyId=idp_enc\",\n"
                    + "    \"uri_puk_idp_sig\": \"http://localhost:${zion.port}/jwk.json?keyId=idp_sig\",\n"
                    + "    \"subject_types_supported\": [\n"
                    + "      \"pairwise\"\n"
                    + "    ],\n"
                    + "    \"id_token_signing_alg_values_supported\": [\n"
                    + "      \"BP256R1\"\n"
                    + "    ],\n"
                    + "    \"response_types_supported\": [\n"
                    + "      \"code\"\n"
                    + "    ],\n"
                    + "    \"scopes_supported\": [\n"
                    + "      \"openid\",\n"
                    + "      \"e-rezept\",\n"
                    + "      \"pairing\",\n"
                    + "      \"authenticator-dev\"\n"
                    + "    ],\n"
                    + "    \"response_modes_supported\": [\n"
                    + "      \"query\"\n"
                    + "    ],\n"
                    + "    \"grant_types_supported\": [\n"
                    + "      \"authorization_code\"\n"
                    + "    ],\n"
                    + "    \"acr_values_supported\": [\n"
                    + "      \"gematik-ehealth-loa-high\"\n"
                    + "    ],\n"
                    + "    \"token_endpoint_auth_methods_supported\": [\n"
                    + "      \"none\"\n"
                    + "    ],\n"
                    + "    \"code_challenge_methods_supported\": [\n"
                    + "      \"S256\"\n"
                    + "    ],\n"
                    + "    \"kk_app_list_uri\": \"http://localhost:${zion.port}/directory/kk_apps\"\n"
                    + "  },\n"
                    + "  \"signature\": {\n"
                    + "    \"verifiedUsing\": \"idp_enc\"\n"
                    + "  }\n"
                    + "}")
                .build())
            .build();
    }
}
