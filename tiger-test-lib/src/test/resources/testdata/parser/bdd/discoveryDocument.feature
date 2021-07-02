@testsuite
@discoveryDocument
Feature: Fordere Discovery Dokument an

    @Afo:A_20668 @Afo:A_19874  @Afo:A_20457  @Afo:A_20688
    @Approval @Ready
    Scenario: Disc - Discovery Dokument muss verfügbar sein

        When I request the discovery document
        Then the response status is 200
        And the response content type matches 'application/json.*'

    @Afo:A_20614 @Afo:A_20623 @Afo:A_20591
    @Approval @Ready
    Scenario: Disc - Discovery Dokument muss signiert sein

        Given I initialize scenario from discovery document endpoint
        And I retrieve public keys from URIs

        When I request the discovery document
        Then the response must be signed with cert PUK_DISC

    @Afo:A_20591
    @Approval @Ready
    Scenario: Disc - Discovery Dokument header claims sind korrekt

        Given I request the discovery document

        When I extract the header claims
        Then the header claims should match in any order
        """
        {
          alg: "BP256R1",
          kid: "${json-unit.ignore}",
          x5c: "${json-unit.ignore}"
        }
        """

    @Approval @Todo:KeyChecksOCSP
    Scenario Outline: Check JWKS URI

        Given I request the discovery document
        And I extract the body claims

        When I request the uri from claim "<claim>" with method GET and status 200
        Then the JSON response should match
        """
          {
            keys: [
              {
                kid:  "puk_idp_enc",
                use:  "enc",
                kty:  "EC",
                crv:  "BP-256",
                x:    ".*",
                y:    ".*"
              },
              {
                x5c:  "${json-unit.ignore}",
                kid:  "puk_idp_sig",
                use:  "sig",
                kty:  "EC",
                crv:  "BP-256",
                x:    ".*",
                y:    ".*"
              }
            ]
          }
        """

        And the JSON array 'keys' of response should contain valid certificates for 'idpSig'
    # The correct usage is then checked in the workflow scenarios

        Examples:
            | claim    |
            | jwks_uri |
