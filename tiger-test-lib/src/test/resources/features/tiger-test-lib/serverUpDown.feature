Feature: Stop and start a server, should work

  Scenario: Stop and restart remote TigerProxy
    Given TGR stop server "remoteTigerProxy"
    And TGR start server "remoteTigerProxy"
    When TGR send empty GET request to "http://localhost:${free.port.1}/httpbin"
    Then TGR find last request to path "/httpbin"
    And TGR current response with attribute "$.responseCode" matches "200"

  Scenario: Stop and restart HttpBin
    Given TGR stop server "httpbin"
    And TGR start server "httpbin"
    When TGR send empty GET request to "http://localhost:${free.port.1}/httpbin"
    Then TGR find last request to path "/httpbin"
    And TGR current response with attribute "$.responseCode" matches "200"
