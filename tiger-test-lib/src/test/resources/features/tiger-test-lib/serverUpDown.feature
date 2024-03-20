Feature: Stop and start a server, should work

  Scenario: Stop and restart remote TigerProxy
    Given TGR stop server "remoteTigerProxy"
    And TGR start server "remoteTigerProxy"
    When TGR send empty GET request to "http://localhost:${free.port.1}/httpbin"
    Then TGR find last request to path "/httpbin"
    And TGR print current response as rbel-tree
    And TGR current response with attribute "$.responseCode" matches "200"
