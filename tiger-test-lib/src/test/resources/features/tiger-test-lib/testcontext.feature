Feature: Provide a test context to test suites to maintain data between steps

  Scenario: Set and get data for context and variables
    Given TGR set global variable "key01" to "value01"
    When TGR assert variable "key01" matches "v.*\d\d"
    Then TGR assert variable "key01" matches ".*"
    Then TGR assert variable "key01" matches "value01"

  Scenario: Set and get data for context and variables
    Given TGR set local variable "key01" to "value01"
    When TGR assert variable "key01" matches "v.*\d\d"
    Then TGR assert variable "key01" matches ".*"

  Scenario: Banner text
    Given TGR show banner "TEST BANNER 1.0"
    And TGR show YELLOW banner "TEST BANNER 2.0"
    And TGR zeige grünen Text "Grüner Text"
