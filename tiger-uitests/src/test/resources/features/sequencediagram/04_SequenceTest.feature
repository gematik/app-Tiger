Feature: Provide a test to check whether symbolic names are found in messages

  Scenario: Talk with server of type 'httpbin' from server of type 'tigerProxy'
    Given TGR send empty GET request to "http://mainServer/helloBackendServer"
    Then TGR find first request to path ".*/helloBackendServer"
    Then TGR current response with attribute "$.responseCode" matches "200"
