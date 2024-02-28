Feature: Playwright Test feature

  Background:
    Given TGR clear recorded messages

  Scenario: Simple Get Request
    When TGR send empty GET request to "http://httpbin"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "GET"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/?"
    And TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "GET"

  Scenario: Get Request to folder
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "GET"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/get\/?"

  Scenario: PUT Request to folder
    When TGR send empty PUT request to "http://httpbin/put"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/put\/?"

  Scenario: PUT Request with body to folder
    When TGR send PUT request to "http://httpbin/put" with:
      | hello |
      | world |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/put\/?"
    And TGR assert "!{rbel:currentRequestAsString('$.body.hello')}" matches "world"

  Scenario: DELETE Request without body
    When TGR send empty DELETE request to "http://httpbin/not_a_file"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "DELET"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/not_a_file\/?"

  Scenario: Request with custom header
    When TGR send empty GET request to "http://httpbin/not_a_file" with headers:
      | foo    | bar |
      | schmoo | lar |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.foo')}" matches "bar"
    And TGR assert "!{rbel:currentRequestAsString('$.header.schmoo')}" matches "lar"

  Scenario: Request with default header
    Given TGR set default header "key" to "value"
    When TGR send empty GET request to "http://httpbin/not_a_file"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"
    When TGR send POST request to "http://httpbin/not_a_file" with:
      | hello |
      | world |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"
    And TGR assert "!{rbel:currentRequestAsString('$.body.hello')}" matches "world"

  Scenario: Request with custom and default header
    Given TGR set default header "key" to "value"
    When TGR send empty GET request to "http://httpbin/not_a_file" with headers:
      | foo | bar |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.header.foo')}" matches "bar"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"

  Scenario: Request with DataTables Test
    Given TGR set local variable "configured_state_value" to "some_value"
    Given TGR set local variable "configured_param_name" to "my_cool_param"
    When TGR send POST request to "http://httpbin/not_a_file" with:
      | ${configured_param_name} | state                     | redirect_uri        |
      | client_id                | ${configured_state_value} | https://my.redirect |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.body.state')}" matches "some_value"
    And TGR assert "!{rbel:currentRequestAsString('$.body.my_cool_param')}" matches "client_id"
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/x-www-form-urlencoded.*"

  Scenario: Request with custom and default header
    Given TGR set default header "Content-Type" to "application/json"
    When TGR send POST request to "http://httpbin/not_a_file" with:
      | ${configured_param_name} |
      | client_id                |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/json"

  Scenario Outline:  Test <color> with <inhalt>
    And TGR show <color> text "<inhalt>"
    Examples: We use this data only for testing data variant display in workflow ui, there is no deeper sense in it
      | color  | inhalt |
      | red    | Dagmar |
      | blue   | Nils   |
      | green  | Tim    |
      | yellow | Sophie |

  Scenario Outline: Test <color> with <text> again
    Given TGR show <color> banner "<text>"
    And TGR clear recorded messages
    Then TGR clear recorded messages
    Examples:
    # Test comment
      | color | text |
      | green | foo  |
    # Test comment
      | red   | bar  |


  Scenario: Test Find Last Request
    Given TGR send empty GET request to "http://httpbin/classes?foobar=1"
    Then TGR send empty GET request to "http://httpbin/classes?foobar=2"
    Then TGR find last request to path "/classes"
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree

  Scenario: Test find last request with parameters
    Given TGR send empty GET request to "http://httpbin/classes?foobar=1"
    Then TGR send empty GET request to "http://httpbin/classes?foobar=1&xyz=4"
    Then TGR send empty GET request to "http://httpbin/classes?foobar=2"
    Then TGR find last request to path "/classes"
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree

  Scenario: Test find last request
    Given TGR send empty GET request to "http://httpbin/classes?foobar=1"
    Then TGR send empty GET request to "http://httpbin/classes?foobar=2"
    Then TGR send empty GET request to "http://httpbin/classes?foobar=3"
    Then TGR send empty GET request to "http://httpbin/directoryWhichDoesNotExist?other=param"
    Then TGR find the last request
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree
    Then TGR current response with attribute "$.responseCode" matches "501"

  Scenario Outline: JEXL Rbel Namespace Test
    Given TGR send empty GET request to "http://httpbin"
    Then TGR find request to path "/"
    And TGR print current request as rbel-tree
    Then TGR current response with attribute "$.body.html.head.title.text" matches "!{rbel:currentResponseAsString('$.body.html.head.title.text')}"
    Examples:
      | txt1  | txt2 | txt3 | txt4 | txt5 |
      | text1 | 21   | 31   | 41   | 51   |
      | text2 | 22   | 32   | 42   | 52   |
      | text3 | 23   | 33   | 43   | 53   |
      | text4 | 24   | 34   | 44   | 54   |
      | text5 | 25   | 35   | 45   | 55   |

  Scenario: Request a non existing url
    When TGR send empty GET request to "http://www.this_is_not_a_real_url_blablabla.com"