Feature: Scenario scope toggle

  Scenario: Scenario without Rbel traffic
    Given TGR setze globale Variable "ScenarioScope" auf "previous"

  Scenario: Request before scoped scenario 1
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 2
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 3
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 4
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 5
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 6
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 7
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 8
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 9
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Request before scoped scenario 10
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"

  Scenario: Simple Get Request for scoped scenario test
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"
