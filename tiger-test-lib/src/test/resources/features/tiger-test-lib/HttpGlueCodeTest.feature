Feature: HTTP/HTTPS GlueCode Test feature

  Background:
    Given TGR clear recorded messages

  Scenario: Simple Get Request
    Given TGR clear recorded messages
    When TGR send empty GET request to "http://httpbin/"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "GET"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/?"

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
    When TGR send PUT request to "http://httpbin/put" with body "{'hello': 'world!'}"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/put\/?"
    And TGR assert "!{rbel:currentRequestAsString('$.body.hello')}" matches "world!"

  Scenario: PUT Request with body from file to folder
    When TGR send PUT request to "http://httpbin/put" with body "!{file('pom.xml')}"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/put\/?"
    And TGR assert "!{rbel:currentRequestAsString('$.body.project.modelVersion.text')}" matches "4.0.0"
   # application/octet-stream is used since no rewriting is done, so unknown/default MIME-type is assumed
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/octet-stream.*"

  Scenario: DELETE Request without body
    When TGR send empty DELETE request to "http://httpbin/delete"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "DELETE"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/delete\/?"

  Scenario: Request with custom header
    When TGR send empty GET request to "http://httpbin/get" with headers:
      | foo    | bar |
      | schmoo | lar |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.foo')}" matches "bar"
    And TGR assert "!{rbel:currentRequestAsString('$.header.schmoo')}" matches "lar"

  Scenario: Request with default header
    Given TGR set default header "key" to "value"
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"
    When TGR send POST request to "http://httpbin/post" with body "hello world"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"
    And TGR assert "!{rbel:currentRequestAsString('$.body')}" matches "hello world"

  Scenario: Request with custom and default header, check headers
    Given TGR set default header "key" to "value"
    When TGR send empty GET request to "http://httpbin/get" with headers:
      | foo | bar |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.foo')}" matches "bar"
    And TGR assert "!{rbel:currentRequestAsString('$.header.key')}" matches "value"

  Scenario: Get Request with custom and default header, check body, application type url encoded
    Given TGR set local variable "configured_state_value" to "some weird $value§"
    Given TGR set local variable "configured_param_name" to "my_cool_param"
    When TGR send GET request to "http://httpbin/get" with:
      | ${configured_param_name} | state                     | redirect_uri        |
      | client_id                | ${configured_state_value} | https://my.redirect |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.path.state.value')}" matches "${configured_state_value}"
    And TGR assert "!{rbel:currentRequestAsString('$.path.state')}" matches "state=!{urlEncoded('some weird $value§')}"
    And TGR assert "!{rbel:currentRequestAsString('$.path.my_cool_param')}" matches "${configured_param_name}=client_id"
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/x-www-form-urlencoded.*"

  Scenario: Post Request with custom and default header, check body, application type url encoded
    Given TGR set local variable "configured_state_value" to "some weird $value§"
    Given TGR set local variable "configured_param_name" to "my_cool_param"
    When TGR send POST request to "http://httpbin/post" with:
      | ${configured_param_name} | state                     | redirect_uri        |
      | client_id                | ${configured_state_value} | https://my.redirect |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.body.state')}" matches "!{urlEncoded('some weird $value§')}"
    And TGR assert "!{rbel:currentRequestAsString('$.body.my_cool_param')}" matches "client_id"
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/x-www-form-urlencoded.*"
    And TGR assert "!{rbel:currentRequestAsString('$.body.redirect_uri')}" matches "!{urlEncoded('https://my.redirect')}"

  Scenario: Request with custom and default header, check application type json
    Given TGR set default header "Content-Type" to "application/json"
    When TGR send POST request to "http://httpbin/post" with:
      | ${configured_param_name} |
      | client_id                |
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/json"

  Scenario Outline: JEXL Rbel Namespace Test
    Given TGR send empty GET request to "http://httpbin/html"
    Then TGR find request to path "/html"
    Then TGR current response with attribute "$.body.html.body.h1.text" matches "!{rbel:currentResponseAsString('$.body.html.body.h1.text')}"

    Examples: We use this data only for testing data variant display in workflow ui, there is no deeper sense in it
      | txt   | txt2 | txt3 | txt4 | txt5 |
      | text2 | 21   | 31   | 41   | 51   |
      | text2 | 22   | 32   | 42   | 52   |

  Scenario: Simple first test
    Given TGR send empty GET request to "http://httpbin/html"
    Then TGR find request to path "/html"
    Then TGR current response with attribute "$.body.html.body.h1.text" matches "Herman Melville - Moby-Dick"

  Scenario: Test Find Last Request
    Given TGR send empty GET request to "http://httpbin/anything?foobar=1"
    Then TGR send empty GET request to "http://httpbin/anything?foobar=2"
    Then TGR find last request to path "/anything"
    Then TGR current response with attribute "$.responseCode" matches "200"
    Then TGR current response with attribute "$.body.url.content.foobar.value" matches "2"

  Scenario: Test find last request with parameters
    Given TGR send empty GET request to "http://httpbin/anything?foobar=1"
    Then TGR send empty GET request to "http://httpbin/anything?foobar=1&xyz=4"
    Then TGR send empty GET request to "http://httpbin/anything?foobar=2"
    Then TGR find last request to path "/anything" with "$.path.foobar.value" matching "1"
    Then TGR current response with attribute "$.body.url.content.xyz.value" matches "4"

  Scenario: Test find last request
    Given TGR send empty GET request to "http://httpbin/anything?foobar=1"
    Then TGR send empty GET request to "http://httpbin/anything?foobar=2"
    Then TGR send empty GET request to "http://httpbin/anything?foobar=3"
    Then TGR send empty GET request to "http://httpbin/status/404?other=param"
    Then TGR find the last request
    Then TGR current response with attribute "$.responseCode" matches "404"
    Then TGR assert "!{rbel:currentRequestAsString('$.path.other.value')}" matches "param"

  Scenario: Get Request to folder and test param is url decoded when access via $.path and ..value is url decoded
    When TGR send empty GET request to "http://httpbin/get?foo=bar%20and%20schmar"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.path.foo.value')}" matches "bar and schmar"
    And TGR assert "!{rbel:currentRequestAsString('$.path.foo')}" matches "foo=bar%20and%20schmar"

  Scenario: Test deactivate followRedirects
    When TGR disable HttpClient followRedirects configuration
    And TGR send empty GET request to "http://httpbin/redirect-to?url=!{urlEncoded('http://httpbin/status/200')}"
    Then TGR find the last request
    Then TGR current response with attribute "$.responseCode" matches "302"
    And TGR current response with attribute "$.header.Location" matches "http://httpbin/status/200"
    When TGR reset HttpClient followRedirects configuration
    And TGR send empty GET request to "http://httpbin/redirect-to?url=!{urlEncoded('http://httpbin/status/200')}"
    Then TGR find the last request
    Then TGR current response with attribute "$.responseCode" matches "200"

  Scenario: Test check filter POST request
    Given TGR send POST request to "http://httpbin/post" with body "{'foobar': '4'}"
    And TGR send empty GET request to "http://httpbin/anything?foobar=22"
    And TGR filter requests based on method "POST"
    Then TGR find last request to path ".*"
    Then TGR current response with attribute "$.body.data.foobar" matches "4"

  Scenario: Test check filter GET request
    Given TGR send empty GET request to "http://httpbin/anything?foobar=22"
    And TGR send POST request to "http://httpbin/post" with body "{'foobar': '4'}"
    And TGR filter requests based on method "GET"
    Then TGR find last request to path ".*"
    Then TGR current response with attribute "$.body.args.foobar" matches "22"

  Scenario: Test check filter method reset
    Given TGR reset request method filter
   # check resetting it works even if done twice
    And TGR reset request method filter
    And TGR send empty GET request to "http://httpbin/anything?foobar=22"
    And TGR send POST request to "http://httpbin/post" with body "{'foobar': '4'}"
    Given TGR reset request method filter
    Then TGR find the last request
    Then TGR current response with attribute "$.body.data.foobar" matches "4"

  Scenario: Test check filter POST request
    Given TGR send empty GET request to "http://httpbin/anything?foobar=66"
    When TGR find last request with "$.path.foobar.value" matching "66"
    Then TGR current response with attribute "$.body.args.foobar.content" matches "66"

