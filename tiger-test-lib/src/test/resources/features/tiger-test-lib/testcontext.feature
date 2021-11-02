Feature: Provide a test context to testsuites to maintain data between steps

  Scenario Outline: Set and get data for context and variables
    Given TGR set <domaintype> domain to "test001"
    When TGR set <type> "key01" to "value01"
    Then TGR assert <type> "key01" matches "v.*\d\d"
    Then TGR assert <type> "key01" matches ".*"

    Examples:
      | type          | domaintype |
      | context entry | context    |
      | variable      | variables  |

  Scenario Outline: Set data and check not set in other domain for context and variables
    Given TGR set <domaintype> domain to "test002"
    And  TGR set <type> "key02" to "value02"
    When TGR set <domaintype> domain to "test002a"
    Then TGR assert <type> "key02" matches "$DOESNOTEXIST"

    Examples:
      | type          | domaintype |
      | context entry | context    |
      | variable      | variables  |

    Scenario: Banner text
      Given TGR show banner "TEST BANNER 1.0"
      Given TGR show YELLOW banner "TEST BANNER 2.0"

  Scenario: Check German umlauts
    Given TGR set context domain to "test003"
    And  TGR set context entry "test01" to "testvalue"
    And  TGR set context entry "test02" to "täßtvalÜ"
    Then TGR assert context matches file 'classpath:/testdata/testdata080.properties'

