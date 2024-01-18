Feature: Provide a test to check whether symbolic names are found in messages

  Scenario: Check if symbolic names and corresponding values are present
    Given TGR send GET request to "http://mainServer/helloWorld" with:
      | password |
      | secret   |

  Scenario: Check if symbolic names work for external url
    Given TGR send empty GET request to "http://exampleCom"