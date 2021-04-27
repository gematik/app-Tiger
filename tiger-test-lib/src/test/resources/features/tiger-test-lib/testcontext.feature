Feature: Provide a test context to testsuites to maintain data between steps

  Scenario: Set and get data
    Given CTXT I set domain to "test001"
    When CTXT I set key "key01" to "value01"
    Then CTXT assert key "key01" matches "v.*\d\d"
    Then CTXT assert key "key01" matches ".*"

  Scenario: Set data and check not set in other domain
    Given CTXT I set domain to "test002"
    And CTXT I set key "key02" to "value02"
    When CTXT I set domain to "test002a"
    Then CTXT assert key "key02" matches "$DOESNOTEXIST"