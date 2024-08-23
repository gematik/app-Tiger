Feature: Custom Fail Message

  Scenario: Test custom fail message
    Given TGR the custom failure message is set to "Hello, this is a custom message"
    And TGR set global variable "blub" to "hello"
    Then TGR assert variable "blub" matches "ble"