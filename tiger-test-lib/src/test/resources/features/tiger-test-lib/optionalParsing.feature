Feature: Provide a test to check if optional parsing works

  Background:
    Given TGR all optional rbel parsers are deactivated

  Scenario: Parse Tgr file check response 1
    Given TGR the rbel parsing is activated for "X509"
    Given TGR reads the following .tgr file "src/test/resources/testdata/rezepsFiltered.tgr"
    Then TGR find last request to path "/VAUCertificate"
    And TGR current response with attribute "$.body.issuer.O" matches "gematik GmbH NOT-VALID"
