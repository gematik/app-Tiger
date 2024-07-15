Feature: TigerGlue Test feature - First File

  Scenario: Test that setting a local variable will override a global variable
    Given TGR set local feature variable "tiger.tigerGlue.helloTestFeature1" to "local value feature 1st file"
    Given TGR set local variable "tiger.tigerGlue.helloTestLocal" to "local value 1st file"
    Then TGR assert variable "tiger.tigerGlue.helloTestLocal" matches "local value 1st file"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature1" matches "local value feature 1st file"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature2" matches "global value for helloTestFeature2"

  Scenario: Test that the change of the local variable does not persist to this test
    Then TGR assert variable "tiger.tigerGlue.helloTestLocal" matches "global value for helloTestLocal"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature1" matches "local value feature 1st file"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature2" matches "global value for helloTestFeature2"


