Feature: Test Tiger BDD

  Scenario: Dummy Test
    Given TGR set global variable "key01" to "value01"
    When TGR assert variable "key01" matches "v.*\d\d"
