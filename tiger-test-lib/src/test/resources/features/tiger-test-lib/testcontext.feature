Feature: Provide a test context to testsuites to maintain data between steps

  Scenario Outline: Set and get data for context and variables
    Given TGR I set <domaintype> domain to "test001"
    When TGR I set <type> "key01" to "value01"
    Then TGR assert <type> "key01" matches "v.*\d\d"
    Then TGR assert <type> "key01" matches ".*"

    Examples:
      | type          | domaintype |
      | context entry | context    |
      | variable      | variables  |

  Scenario Outline: Set data and check not set in other domain for context and variables
    Given TGR I set <domaintype> domain to "test002"
    And  TGR I set <type> "key02" to "value02"
    When TGR I set <domaintype> domain to "test002a"
    Then TGR assert <type> "key02" matches "$DOESNOTEXIST"

    Examples:
      | type          | domaintype |
      | context entry | context    |
      | variable      | variables  |
