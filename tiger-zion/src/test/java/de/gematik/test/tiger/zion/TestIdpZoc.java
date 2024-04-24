package de.gematik.test.tiger.zion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import de.gematik.idp.IdpConstants;
import de.gematik.idp.client.IdpClient;
import de.gematik.idp.crypto.model.PkiIdentity;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.zion.config.TigerMockResponse;
import de.gematik.test.tiger.zion.config.TigerMockResponseDescription;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
@TestPropertySource(
    properties = {"zion.mockResponseFiles.firstFile=src/test/resources/someMockResponse.yaml"})
class TestIdpZoc {

  final Path tempDirectory = Path.of("target", "zionResponses");

  @Autowired private ZionConfiguration configuration;
  @LocalServerPort private int port;
  private Map<String, TigerMockResponse> mockResponsesBackup;

  @SneakyThrows
  @BeforeEach
  public void setupTempDirectory() {
    TigerGlobalConfiguration.reset();
    Files.createDirectories(tempDirectory);
    Files.list(tempDirectory).forEach(path -> path.toFile().delete());
    mockResponsesBackup = configuration.getMockResponses();
  }

  @AfterEach
  public void resetMockResponses() {
    TigerGlobalConfiguration.reset();
    configuration.setMockResponses(mockResponsesBackup);
    configuration.setSpy(null);
  }

  @Test
  void testIdpClient() {
    configuration.setMockResponses(
        Map.of(
            "discovery_document", discoveryDocumentMockResponse(),
            "key_endpoints", keyEndpoints(),
            "sign_response", signResponse(),
            "sign_response_post", signResponsePost(),
            "token", tokenEndpoint()));

    final IdpClient idpClient =
        IdpClient.builder()
            .clientId("eRezeptApp")
            .discoveryDocumentUrl(
                "http://localhost:" + port + IdpConstants.DISCOVERY_DOCUMENT_ENDPOINT)
            .redirectUrl("http://redirect.gematik.de/erezept")
            .build();
    final TigerPkiIdentity clientIdentity =
        new TigerPkiIdentity("src/test/resources/egk_identity.p12;00");

    assertDoesNotThrow(
        () -> {
          idpClient.initialize();
          idpClient.login(
              PkiIdentity.builder()
                  .certificate(clientIdentity.getCertificate())
                  .privateKey(clientIdentity.getPrivateKey())
                  .build());
        });
  }

  private TigerMockResponse signResponsePost() {
    return TigerMockResponse.builder()
        .requestCriterions(
            List.of("message.method == 'POST'", "message.url =~ '.*/sign_response?.*'"))
        .response(
            TigerMockResponseDescription.builder()
                .statusCode("302")
                .headers(
                    Map.of(
                        "Location",
                        """
                    {
                      "tgrEncodeAs": "url",
                      "basicPath": "http://redirect.gematik.de/erezept",
                      "parameters": {
                        "state": "?{$..body.state.content}",
                        "code": {
                          "tgrEncodeAs": "JWE",
                          "header": {
                            "alg": "ECDH-ES",
                            "enc": "A256GCM",
                            "cty": "NJWT"
                          },
                          "body": {
                            "njwt": {
                              "tgrEncodeAs": "JWT",
                              "header": {
                                "alg": "BP256R1",
                                "typ": "JWT",
                                "kid": "puk_idp_sig"
                              },
                              "body": {
                                "organizationName": "Test GKV-SVNOT-VALID",
                                "professionOID": "?{$..7..[?(@._0 == '1.3.36.8.3.3')].1..1.0}",
                                "idNummer": "?{$..x5c.0.content.subject.[?(key=='OU' && content =^ 'X')]}",
                                "amr": ["mfa","sc","pin"],
                                "iss": "http://url.des.idp",
                                "response_type": "code",
                                "snc": "BC8n0vKfxTiFZGdLtZzf",
                                "code_challenge_method": "S256",
                                "given_name": "?{$..x5c.0.content.subject.GN}",
                                "token_type": "code",
                                "nonce": "?{$..nonce.content}",
                                "client_id": "?{$..client_id.content}",
                                "scope": "?{$..scope.content}",
                                "auth_time": 1667404477,
                                "redirect_uri": "?{$..redirect_uri.content}",
                                "state": "?{$..body.state.content}",
                                "exp": 1667404537,
                                "family_name": "?{$..x5c.0.content.subject.SURNAME}",
                                "iat": 1667404477,
                                "code_challenge": "?{$..code_challenge.content}",
                                "jti": "?{$..jti.content}"
                              },
                              "signature": {
                                "verifiedUsing": "idp_sig"
                              }
                            }
                          },
                          "encryptionInfo": {
                            "decryptedUsingKeyWithId": "puk_idp_enc"
                          }
                        }
                      }
                    }
                    """))
                .build())
        .build();
  }

  private TigerMockResponse signResponse() {
    return TigerMockResponse.builder()
        .requestCriterions(
            List.of("message.method == 'GET'", "message.url =~ '.*/sign_response?.*'"))
        .response(
            TigerMockResponseDescription.builder()
                .body(
                    """
                    {
                      "challenge": {
                          "tgrEncodeAs": "JWT",
                          "header": {
                            "alg": "BP256R1",
                            "typ": "JWT",
                            "kid": "puk_idp_sig"
                          },
                          "body": {
                            "iss": "http://url.des.idp",
                            "response_type": "code",
                            "snc": "uJdv7zlE6Fh1I0Xl6MSBFA-VUgD3nL7U6i2W2P2pUrA",
                            "code_challenge_method": "S256",
                            "token_type": "challenge",
                            "nonce": "?{$.path.nonce.value}",
                            "client_id": "?{$.path.client_id.value}",
                            "scope": "openid e-rezept",
                            "state": "dU9TWgLIoSTLIEBXgftr",
                            "redirect_uri": "http://redirect.gematik.de/erezept",
                            "exp": 1667404656,
                            "iat": 1667404476,
                            "code_challenge": "ztagmrO3wT6_3eNn9jRJO_exBvxn8xj1SKRPNN1wuf0",
                            "jti": "03d29fb8634fdfa2"
                          },
                          "signature": {
                            "verifiedUsing": "idp_sig"
                          }
                       \s
                      }
                    }
                    """)
                .build())
        .build();
  }

  private TigerMockResponse keyEndpoints() {
    return TigerMockResponse.builder()
        .requestCriterions(List.of("message.method == 'GET'", "message.url =~ '.*jwk.json?.*'"))
        .response(
            TigerMockResponseDescription.builder()
                .body(
                    """
                    {
                      "x5c": [
                        "!{keyMgr.b64Certificate('puk_?{$.path.keyId.value}')}"
                      ],
                      "use": "sig",
                      "kid": "puk_idp_sig",
                      "kty": "EC",
                      "crv": "BP-256",
                      "x": "llCsbU1bEgHeTP_pnbOiOWQmo3e8ldncRmcnoldNfDk",
                      "y": "ZDFZ5XjwWmtgfomv3VOV7qzI5ycUSJysMWDEu3mqRcY"
                    }
                    """)
                .build())
        .build();
  }

  private static TigerMockResponse discoveryDocumentMockResponse() {
    return TigerMockResponse.builder()
        .requestCriterions(
            List.of(
                "message.method == 'GET'", "message.url =$ '/.well-known/openid-configuration'"))
        .response(
            TigerMockResponseDescription.builder()
                .body(
                    """
                    {
                      "tgrEncodeAs": "JWT",
                      "header": {
                        "alg": "BP256R1",
                        "typ": "JWT",
                        "kid": "puk_disc_sig",
                        "x5c": ["!{keyMgr.b64Certificate('puk_disc_sig')}"]
                      },
                      "body": {
                        "authorization_endpoint": "http://localhost:${zion.port}/sign_response",
                        "auth_pair_endpoint": "http://localhost:${zion.port}/alt_response",
                        "sso_endpoint": "http://localhost:${zion.port}/sso_response",
                        "uri_pair": "http://localhost:${zion.port}/pairings",
                        "token_endpoint": "http://localhost:${zion.port}/token",
                        "third_party_authorization_endpoint": "http://localhost:${zion.port}/extauth",
                        "uri_disc": "http://localhost:${zion.port}/.well-known/openid-configuration",
                        "issuer": "https://idp.zentral.idp.splitdns.ti-dienste.de",
                        "jwks_uri": "http://localhost:${zion.port}/jwks",
                        "exp": 1667490873,
                        "iat": 1667404473,
                        "uri_puk_idp_enc": "http://localhost:${zion.port}/jwk.json?keyId=idp_enc",
                        "uri_puk_idp_sig": "http://localhost:${zion.port}/jwk.json?keyId=idp_sig",
                        "subject_types_supported": [
                          "pairwise"
                        ],
                        "id_token_signing_alg_values_supported": [
                          "BP256R1"
                        ],
                        "response_types_supported": [
                          "code"
                        ],
                        "scopes_supported": [
                          "openid",
                          "e-rezept",
                          "pairing",
                          "authenticator-dev"
                        ],
                        "response_modes_supported": [
                          "query"
                        ],
                        "grant_types_supported": [
                          "authorization_code"
                        ],
                        "acr_values_supported": [
                          "gematik-ehealth-loa-high"
                        ],
                        "token_endpoint_auth_methods_supported": [
                          "none"
                        ],
                        "code_challenge_methods_supported": [
                          "S256"
                        ],
                        "kk_app_list_uri": "http://localhost:${zion.port}/directory/kk_apps"
                      },
                      "signature": {
                        "verifiedUsing": "idp_enc"
                      }
                    }
                    """)
                .build())
        .build();
  }

  private TigerMockResponse tokenEndpoint() {
    return TigerMockResponse.builder()
        .requestCriterions(List.of("message.method == 'POST'", "message.url =~ '.*/token'"))
        .response(
            TigerMockResponseDescription.builder()
                .bodyFile("src/test/resources/idpTokenEndpointResponseBody.json")
                .build())
        .build();
  }
}
