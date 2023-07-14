Feature: HTTP/HTTPS GlueCode Test feature

  Background:
    Given TGR clear recorded messages

  Scenario: Simple Get Request
    When TGR send empty GET request to "http://winstone"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "GET"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/?"

  Scenario: Get Request to folder
    When TGR send empty GET request to "http://winstone/target"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "GET"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/target\/?"

  Scenario: PUT Request to folder
    When TGR send empty PUT request to "http://winstone/target"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/target\/?"

  Scenario: PUT Request with body to folder
    When TGR send PUT request to "http://winstone/target" with body "{'hello': 'world!'}"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/target\/?"
    And TGR assert "!{rbel:currentRequestAsString('$.body.hello')}" matches "world!"

  Scenario: PUT Request with body from file to folder
    When TGR send PUT request to "http://winstone/target" with body "!{file('pom.xml')}"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/target\/?"
    And TGR assert "!{rbel:currentRequestAsString('$.body.project.modelVersion.text')}" matches "4.0.0"
    # application/octet-stream is used since no rewriting is done, so unknown/default MIME-type is assumed
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/octet-stream.*"

  Scenario: DELETE Request without body
    When TGR send empty DELETE request to "http://winstone/not_a_file"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "DELETE"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/not_a_file\/?"

  Scenario: Request with custom header
    When TGR send empty GET request to "http://winstone/not_a_file" with headers:
      | foo    | bar |
      | schmoo | lar |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.foo')}" matches "bar"
    And TGR assert "!{rbel:currentRequestAsString('$.header.schmoo')}" matches "lar"

  Scenario: Request with default header
    Given TGR set default header "key" to "value"
    When TGR send empty GET request to "http://winstone/not_a_file"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"
    When TGR send POST request to "http://winstone/not_a_file" with body "hello world"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"
    And TGR assert "!{rbel:currentRequestAsString('$.body')}" matches "hello world"

  Scenario: Request with custom and default header
    Given TGR set default header "key" to "value"
    When TGR send empty GET request to "http://winstone/not_a_file" with headers:
      | foo | bar |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.header.foo')}" matches "bar"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"

  Scenario: Request with custom and default header
    Given TGR set local variable "configured_state_value" to "some_value"
    Given TGR set local variable "configured_param_name" to "my_cool_param"
    When TGR send POST request to "http://winstone/not_a_file" with:
      | ${configured_param_name}   | state                     | redirect_uri        |
      | client_id                  | ${configured_state_value} | https://my.redirect |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.body.state')}" matches "some_value"
    And TGR assert "!{rbel:currentRequestAsString('$.body.my_cool_param')}" matches "client_id"
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/x-www-form-urlencoded.*"

  Scenario: Request with custom and default header
    Given TGR set default header "Content-Type" to "application/json"
    When TGR send POST request to "http://winstone/not_a_file" with:
      | ${configured_param_name}   |
      | client_id                  |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/json"

  Scenario Outline: JEXL Rbel Namespace Test
    Given TGR send empty GET request to "http://winstone"
    Then TGR find request to path "/"
    And TGR print current request as rbel-tree
    Then TGR current response with attribute "$.body.html.head.link.href" matches "!{rbel:currentResponseAsString('$.body.html.head.link.href')}"

    Examples: We use this data only for testing data variant display in workflow ui, there is no deeper sense in it
      | txt   | txt2 | txt3| txt4| txt5|
      | text2 | 21   |31   |41   |51   |
      | text2 |22    |32   |42   |52   |

  Scenario: Simple first test
    Given TGR send empty GET request to "http://winstone"
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"

  Scenario: Test Find Last Request
    Given TGR send empty GET request to "http://winstone/classes?foobar=1"
    Then TGR send empty GET request to "http://winstone/classes?foobar=2"
    Then TGR find last request to path "/classes"
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree
    Then TGR current response with attribute "$.header.Location.foobar.value" matches "2"

  Scenario: Test find last request with parameters
    Given TGR send empty GET request to "http://winstone/classes?foobar=1"
    Then TGR send empty GET request to "http://winstone/classes?foobar=1&xyz=4"
    Then TGR send empty GET request to "http://winstone/classes?foobar=2"
    Then TGR find last request to path "/classes" with "$.path.foobar.value" matching "1"
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree
    Then TGR current response with attribute "$.header.Location.xyz.value" matches "4"
    Then TGR current response with attribute "$.header.Location.xyz.value" matches "4"

  Scenario: Test find last request
    Given TGR send empty GET request to "http://winstone/classes?foobar=1"
    Then TGR send empty GET request to "http://winstone/classes?foobar=2"
    Then TGR send empty GET request to "http://winstone/classes?foobar=3"
    Then TGR send empty GET request to "http://winstone/directoryWhichDoesNotExist?other=param"
    Then TGR find the last request
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree
    Then TGR current response with attribute "$.responseCode" matches "404"
    Then TGR assert "!{rbel:currentRequestAsString('$.path.other.value')}" matches "param"
