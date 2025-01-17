Feature: Provide a test to check whether symbolic names are found in messages

  Scenario: Talk with server of type 'zion' from server of type 'zion'
    Given TGR send empty GET request to "http://mainServer/helloZionBackendServer"
    Then TGR find request to path ".*/helloZionBackendServer"
    Then TGR current response with attribute "$.responseCode" matches "200"
    Then TGR current response with attribute "$.body.Hello" matches "from backend"
