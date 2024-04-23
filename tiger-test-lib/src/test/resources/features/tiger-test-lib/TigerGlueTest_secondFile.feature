Feature: TigerGlue Test feature - Second File

  Scenario: Test that setting a local variable will override a global variable
    Given TGR set local feature variable "tiger.tigerGlue.helloTestFeature2" to "local value feature 2nd file"
    Given TGR set local variable "tiger.tigerGlue.helloTestLocal" to "local value 2nd file"
    Then TGR assert variable "tiger.tigerGlue.helloTestLocal" matches "local value 2nd file"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature1" matches "global value for helloTestFeature1"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature2" matches "local value feature 2nd file"

  Scenario: Test that the change of the local variable does not persist to this test
    Then TGR assert variable "tiger.tigerGlue.helloTestLocal" matches "global value for helloTestLocal"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature1" matches "global value for helloTestFeature1"
    Then TGR assert variable "tiger.tigerGlue.helloTestFeature2" matches "local value feature 2nd file"