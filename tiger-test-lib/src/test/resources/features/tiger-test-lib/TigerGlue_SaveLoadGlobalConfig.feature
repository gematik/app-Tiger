Feature: TigerGlue Test saving and loading global config

  Scenario: Test save and load global configuration
    Given TGR set global variable "test.fileSave" to "beforeFileSave"
    And TGR save TigerGlobalConfiguration to file "target/tiger_global_configuration.yaml"
    And TGR set global variable "test.fileSave" to "afterFileSave"
    Then TGR assert variable "test.fileSave" matches "afterFileSave"
    And TGR load TigerGlobalConfiguration from file "target/tiger_global_configuration.yaml"
    Then TGR assert variable "test.fileSave" matches "beforeFileSave"