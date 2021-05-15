#
# ${GEMATIK_COPYRIGHT_STATEMENT}
#

@testsuite
@SignedChallengeFlow
Feature: Fordere Access Token mit einer signierten Challenge an
  Frontends von TI Diensten müssen vom IDP Server über ein HTTP POST an den Token Endpoint ein Access/SSO/ID Token abfragen können.

  Background: Initialisiere Testkontext durch Abfrage des Discovery Dokuments
    Given I initialize scenario from discovery document endpoint
    And I retrieve public keys from URIs

  @Afo:A_20463 @Afo:A_20321
  @Approval @Ready
  Scenario: GetToken Signierte Challenge - Gutfall - Check Access Token - Validiere Antwortstruktur
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 887766 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'

    When I request an access token
    Then the response status is 200
    And the JSON response should match
        """
          { access_token: "ey.*",
            expires_in:   300,
            id_token:     "ey.*",
            token_type:   "Bearer"
          }
        """

  @Afo:A_20731 @Afo:A_20310 @Afo:A_20464 @Afo:A_20952 @Afo:A_21320 @Afo:A_21321 @Afo:A_20313
  @Approval @Todo:AccessTokenContent
  @Todo:CompareSubjectInfosInAccessTokenAndInCert
    # TODO: wollen wir noch den Wert der auth_time gegen den Zeitpunkt der Authentifizierung pruefen
  Scenario: GetToken Signierte Challenge - Gutfall - Check Access Token - Validiere Access Token Claims
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 887766 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'
    And I request an access token

    When I extract the header claims from token ACCESS_TOKEN
    Then the header claims should match in any order
        """
          { alg: "BP256R1",
            kid: "${json-unit.ignore}",
            typ: "at+JWT"
          }
        """
    When I extract the body claims from token ACCESS_TOKEN
    Then the body claims should match in any order
        """
          { acr:              "gematik-ehealth-loa-high",
            amr:              ["mfa", "sc", "pin"],
            aud:              "https://erp.telematik.de/login",
            auth_time:        "[\\d]*",
            azp:              "${TESTENV.client_id}",
            client_id:        "${TESTENV.client_id}",
            exp:              "[\\d]*",
            jti:              "${json-unit.ignore}",
            family_name:      "(.{1,64})",
            given_name:       "(.{1,64})",
            iat:              "[\\d]*",
            idNummer:         "[A-Z][\\d]{9,10}",
            iss:              "${TESTENV.issuer}",
            organizationName: "(.{1,64})",
            professionOID:    "1\\.2\\.276\\.0\\.76\\.4\\.(3\\d|4\\d|178|23[2-90]|240|241)",
            scope:            "${TESTENV.scopes_basisflow_regex}",
            sub:              ".*"
          }
        """

        # TODO organizationName bei HBA nicht gesetzt
        # TODO bei SMC-B sind names optional, wie gehen wir damit um?
        # TODO Zu klären: wo prüfen wir die gültigkeit der professionOID am server? oder akzeptieren wir was in der Karte steht?
        # 1\\.2\\.276\\.0\\.76\\.4\\.(3\\d|4\\d|178|23[2-0]|240|241)

  @Approval @Ready
  @Todo:WeAlreadyHaveTheseChecksInAnotherTestcase @Todo:Duplicate
  Scenario: GetToken Signierte Challenge - Gutfall - Check ID Token - Validiere Antwortstruktur
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 98765 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'

    When I request an access token
    Then the response status is 200
    And the JSON response should match
        """
          { access_token: "ey.*",
            expires_in:   300,
            id_token:     "ey.*",
            token_type:   "Bearer"
          }
        """

  @Afo:A_21321 @Afo:A_20313
  @Approval @Ready
  Scenario: GetToken Signierte Challenge - Gutfall - Check ID Token - Validiere ID Token Claims
  ```
  Validierungen:

  at_hash ist base64 url encoded (enthält keine URL inkompatiblen Zeichen +/=)

    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 98765 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'
    And I request an access token

    When I extract the header claims from token ID_TOKEN
    Then the header claims should match in any order
        """
          { alg: "BP256R1",
            kid: "${json-unit.ignore}",
            typ: "JWT"
          }
        """
    When I extract the body claims from token ID_TOKEN
    Then the body claims should match in any order
        """
          { acr:              "gematik-ehealth-loa-high",
            amr:              ["mfa","sc","pin"],
            at_hash:          "[A-Za-z0-9\\-\\_]*",
            aud:              "${TESTENV.client_id}",
            auth_time:        "[\\d]*",
            azp:              "${TESTENV.client_id}",
            exp:              "[\\d]*",
            family_name:      "(.{1,64})",
            given_name:       "(.{1,64})",
            iat:              "[\\d]*",
            idNummer:         "[A-Z][\\d]{9,10}",
            iss:              "${TESTENV.issuer}",
            nonce:            "98765",
            professionOID:    "1\\.2\\.276\\.0\\.76\\.4\\.(3\\d|4\\d|178|23[2-90]|240|241)",
            organizationName: "(.{1,64})",
            sub:              ".*",
            jti:              ".*"
          }
        """

  @Approval @Todo:AccessTokenContent
  Scenario: GetToken Signierte Challenge - Subject Claim ist abhängig von idNummer
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 887766 | code          |
    And I sign the challenge with '/certs/valid/80276883110000129089-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'
    And I request an access token

    When I extract the body claims from token ACCESS_TOKEN
    Then the body claim 'sub' should match '.*'
    And the body claim 'idNummer' should match "[A-Z][\d]{9,10}"
    When I extract the body claims from token ID_TOKEN
    Then the body claim 'sub' should match '.*'
    And the body claim 'idNummer' should match "[A-Z][\d]{9,10}"

    # TODO write method to save a specific claim and compare it with another (positive and negative)
    # TODO rewrite test case to run two times and verify that sub values do mismatch

  @Approval @Todo:AccessTokenContent
  Scenario: GetToken Signierte Challenge - Subject Claim wird auch für nicht durch Versicherte signierte Challenges erstellt
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 887766 | code          |
    And I sign the challenge with '/certs/valid/80276883110000129083-C_HP_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'
    And I request an access token

    When I extract the body claims from token ACCESS_TOKEN
    Then the body claim 'sub' should match '.*'
    And the body claim 'idNummer' should match "[\d]\-.*"
    When I extract the body claims from token ID_TOKEN
    Then the body claim 'sub' should match '.*'
    And the body claim 'idNummer' should match "[\d]\-.*"

  @Afo:A_20327
  @Approval @Ready
  @Signature
  Scenario: GetToken Signierte Challenge - Validiere Signatur Access Token
    Given I retrieve public keys from URIs
    And I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 888877 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'

    When I request an access token
    Then the context ACCESS_TOKEN must be signed with cert PUK_SIGN

  @Afo:A_20625 @Afo:A_20327
  @Approval @Ready
  @Signature
  Scenario: GetToken Signierte Challenge - Validiere Signatur ID Token
    Given I retrieve public keys from URIs
    And I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 888877 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'

    When I request an access token
    Then the context ID_TOKEN must be signed with cert PUK_SIGN

    # TODO card specific cases (if user consent claims should be validated)


  @Afo:A_20314 @Afo:A_20315
  @Approval @Ready @LongRunning
  @Timeout
  Scenario: GetToken Signierte Challenge - Veralteter Token code wird abgelehnt
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 98765 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'

    When I wait PT65S
    And I request an access token
    Then the response is an 400 error with gematik code 3011 and error 'invalid_grant'

  @Approval @Ready
  Scenario Outline: GetToken Signierte Challenge - Null Parameter
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 777766 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'
    When I request an access token with
      | grant_type   | redirect_uri   | token_code   | code_verifier   | client_id   |
      | <grant_type> | <redirect_uri> | <token_code> | <code_verifier> | <client_id> |
    Then the response status is 400
    And the JSON response should match
        """
          { error:              "<err_code>",
	        gematik_error_text: ".*",
	        gematik_timestamp:  "[\\d]*",
	        gematik_uuid:       ".*",
	        gematik_code:       "<err_id>"
          }
        """

    # TODO check error detail message

    Examples: GetToken - Null Parameter Beispiele
      | err_id | err_code               | grant_type         | redirect_uri            | token_code | code_verifier              | client_id            |
      | 3014   | unsupported_grant_type | $NULL              | ${TESTENV.redirect_uri} | $CONTEXT   | ${TESTENV.code_verifier01} | ${TESTENV.client_id} |
      | 1020   | invalid_request        | authorization_code | $NULL                   | $CONTEXT   | ${TESTENV.code_verifier01} | ${TESTENV.client_id} |
      | 3010   | invalid_grant          | authorization_code | ${TESTENV.redirect_uri} | $NULL      | ${TESTENV.code_verifier01} | ${TESTENV.client_id} |
      | 3015   | invalid_request        | authorization_code | ${TESTENV.redirect_uri} | $CONTEXT   | $NULL                      | ${TESTENV.client_id} |
      | 3007   | invalid_client         | authorization_code | ${TESTENV.redirect_uri} | $CONTEXT   | ${TESTENV.code_verifier01} | $NULL                |

  @Approval @Ready
  Scenario Outline: GetToken Signierte Challenge - Fehlende Parameter
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 776655 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    When I request an access token with
      | grant_type   | redirect_uri   | token_code   | code_verifier   | client_id   |
      | <grant_type> | <redirect_uri> | <token_code> | <code_verifier> | <client_id> |
    Then the response status is 400
    And the JSON response should match
        """
          { error:              "<err_code>",
	        gematik_error_text: ".*",
	        gematik_timestamp:  "[\\d]*",
	        gematik_uuid:       ".*",
	        gematik_code:       "<err_id>"
          }
        """

    Examples: GetToken - Fehlende Parameter Beispiele
      | err_id | err_code        | grant_type         | redirect_uri            | token_code | code_verifier              | client_id            |
      | 3006   | invalid_request | $REMOVE            | ${TESTENV.redirect_uri} | $CONTEXT   | ${TESTENV.code_verifier01} | ${TESTENV.client_id} |
      | 1004   | invalid_request | authorization_code | $REMOVE                 | $CONTEXT   | ${TESTENV.code_verifier01} | ${TESTENV.client_id} |
      | 3010   | invalid_grant   | authorization_code | ${TESTENV.redirect_uri} | $REMOVE    | ${TESTENV.code_verifier01} | ${TESTENV.client_id} |
      | 3015   | invalid_request | authorization_code | ${TESTENV.redirect_uri} | $CONTEXT   | $REMOVE                    | ${TESTENV.client_id} |
      | 1002   | invalid_request | authorization_code | ${TESTENV.redirect_uri} | $CONTEXT   | ${TESTENV.code_verifier01} | $REMOVE              |


  #noinspection NonAsciiCharacters
  @Approval @Ready
  Scenario Outline: GetToken Signierte Challenge - Ungültige Parameter
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 776655 | code          |
    And I sign the challenge with '/certs/valid/80276883110000018680-C_CH_AUT_E256.p12'
    And I request a code token with signed challenge
    When I request an access token with
      | grant_type   | redirect_uri   | token_code   | code_verifier   | client_id   |
      | <grant_type> | <redirect_uri> | <token_code> | <code_verifier> | <client_id> |
    Then the response status is 400
    And the JSON response should match
        """
          { error:              "<err_code>",
	        gematik_error_text: ".*",
	        gematik_timestamp:  "[\\d]*",
	        gematik_uuid:       ".*",
	        gematik_code:       "<err_id>"
          }
        """

    Examples: GetToken - Ungültige Parameter Beispiele
      | err_id | err_code               | grant_type         | redirect_uri                   | token_code                                                                                                                                                | code_verifier                                                                                                                  | client_id            |
      | 3014   | unsupported_grant_type | deepstate_grant    | ${TESTENV.redirect_uri}        | $CONTEXT                                                                                                                                                  | ${TESTENV.code_verifier01}                                                                                                     | ${TESTENV.client_id} |
      | 1020   | invalid_request        | authorization_code | http://www.somethingstore.com/ | $CONTEXT                                                                                                                                                  | ${TESTENV.code_verifier01}                                                                                                     | ${TESTENV.client_id} |
      | 3013   | invalid_request        | authorization_code | ${TESTENV.redirect_uri}        | Ob Regen, Sturm oder Sonnenschein: Dankbare Ergebenheit ist kein Latein. Bleibe nicht länger abhängig vom Wetter, sondern schaue auf den einzigen Retter! | ${TESTENV.code_verifier01}                                                                                                     | ${TESTENV.client_id} |
      | 3016   | invalid_request        | authorization_code | ${TESTENV.redirect_uri}        | $CONTEXT                                                                                                                                                  | Was war das für ein Zaubertraum, der sich in meine Seele glückt? An Tannen gehn die Lichter an und immer weiter wird der Raum. | ${TESTENV.client_id} |
      | 3007   | invalid_client         | authorization_code | ${TESTENV.redirect_uri}        | $CONTEXT                                                                                                                                                  | ${TESTENV.code_verifier01}                                                                                                     | shadows              |


  @Todo:CompareSubjectInfosInAccessTokenAndInCert
    @UserConsent
  Scenario Outline: GetToken Signierte Challenge - Inhalte des Zertifikats entsprechen nicht der Spezifikation und werden akzeptiert, Prüfung der Claims
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 887766 | code          |
    And I sign the challenge with '<cert>'
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'
    And I request an access token

    When I extract the header claims from token ACCESS_TOKEN
    Then the header claims should match in any order
        """
          { alg: "BP256R1",
            kid: "${json-unit.ignore}",
            typ: "at+JWT"
          }
        """
    When I extract the body claims from token ACCESS_TOKEN
    Then the body claim 'family_name' should match '<surname>'
    And the body claim 'given_name' should match '<givenName>'
    And the body claim 'idNummer' should match '<idnumber>'
    And the body claim 'professionOID' should match '<professionOID>'
    And the body claim 'organizationName' should match '<organizationName>'

    Examples: GetToken - Zertifikate und Claims
      | cert                                               | surname                                                           | givenName                                                         | idnumber   | professionOID     | organizationName                                                  |
      | /certs/invalid/egk-idp-famname-toolong-ecc.p12     | Dalllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllll | Luca                                                              | X764228433 | 1.2.276.0.76.4.49 | gematik Musterkasse1GKVNOT-VALID                                  |
      | /certs/invalid/egk-idp-firstname-toolong-ecc.p12   | Dal                                                               | Lucaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | X764228437 | 1.2.276.0.76.4.49 | gematik Musterkasse1GKVNOT-VALID                                  |
      | /certs/invalid/egk-idp-idnum-invalididnum2-ecc.p12 | Dal                                                               | Luca                                                              | 1234567890 | 1.2.276.0.76.4.49 | gematik Musterkasse1GKVNOT-VALID                                  |
      | /certs/invalid/egk-idp-profid-invoid1-ecc.p12      | Dal                                                               | Luca                                                              | X764228437 | 1.2.276.0.76.1    | gematik Musterkasse1GKVNOT-VALID                                  |
      | /certs/invalid/egk-idp-profid-invoid2-ecc.p12      | Dal                                                               | Luca                                                              | X764228437 | 1.2.276.0.76.255  | gematik Musterkasse1GKVNOT-VALID                                  |
      | /certs/invalid/egk-idp-orgname-toolong-ecc.p12     | Dal                                                               | Luca                                                              | X764228437 | 1.2.276.0.76.4.49 | gematik Musterkasse1GKVNOT-VALIDgematik Musterkasse11GKVNOT-VALID |

  @Todo:CompareSubjectInfosInAccessTokenAndInCert
    @UserConsent
  Scenario Outline: GetToken Signierte Challenge - Inhalte des Zertifikats entsprechen nicht der Spezifikation, werden aber akzeptiert, keine Prüfung der Claims
    Given I choose code verifier '${TESTENV.code_verifier01}'
    And I request a challenge with
      | client_id            | scope                      | code_challenge              | code_challenge_method | redirect_uri            | state       | nonce  | response_type |
      | ${TESTENV.client_id} | ${TESTENV.scope_basisflow} | ${TESTENV.code_challenge01} | S256                  | ${TESTENV.redirect_uri} | xxxstatexxx | 887766 | code          |
    And I sign the challenge with <cert>
    And I request a code token with signed challenge
    And I set the context with key REDIRECT_URI to '${TESTENV.redirect_uri}'
    And I request an access token

    When I extract the header claims from token ACCESS_TOKEN
    Then the header claims should match in any order
        """
          { alg: "BP256R1",
            kid: "${json-unit.ignore}",
            typ: "at+JWT"
          }
        """

    Examples: GetToken - Zertifikate und Claims
      | cert                                            |
      | '/certs/invalid/egk-idp-famname-null-ecc.p12'   |
      | '/certs/invalid/egk-idp-firstname-null-ecc.p12' |
      | '/certs/invalid/egk-idp-orgname-null-ecc.p12'   |
      | '/certs/invalid/egk-idp-profid-null-ecc.p12'    |

