Feature: Provide a test to check if parsing of tgr file works

  Scenario: Parse Tgr file check response 1
    Given TGR reads the following .tgr file "src/test/resources/testdata/rezepsFiltered.tgr"
    Then TGR find last request to path "/VAUCertificate"
    And TGR current response with attribute "$.body.issuer.O" matches "gematik GmbH NOT-VALID"


  Scenario: Parse Tgr file check response 2
    Given TGR reads the following .tgr file "src/test/resources/testdata/rezepsFiltered.tgr"
    Then TGR find last request to path "/sign_response"
    And TGR current response with attribute "$.header.Location.code.value.header.enc.content" matches "A256GCM"
