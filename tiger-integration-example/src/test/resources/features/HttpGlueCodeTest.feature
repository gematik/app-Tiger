Feature: HTTP/HTTPS GlueCode Test feature

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
    When TGR send PUT request with "{'hello': 'world!'}" to "http://winstone/target"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/target\/?"
    And TGR assert "!{rbel:currentRequestAsString('$.body.hello')}" matches "world!"

  Scenario: PUT Request with body from file to folder
    When TGR send PUT request with "!{file('pom.xml')}" to "http://winstone/target"
    Then TGR find last request to path ".*"
    And TGR assert "!{rbel:currentRequestAsString('$.method')}" matches "PUT"
    And TGR assert "!{rbel:currentRequestAsString('$.path')}" matches "\/target\/?"
    And TGR assert "!{rbel:currentRequestAsString('$.body.project.modelVersion.text')}" matches "4.0.0"
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "text/plain.*"

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
    When TGR send POST request with "hello world" to "http://winstone/not_a_file"
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
    When Send POST request to "http://winstone/not_a_file" with
      | ${configured_param_name}   | state                     | redirect_uri        |
      | client_id                  | ${configured_state_value} | https://my.redirect |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.body.state')}" matches "some_value"
    And TGR assert "!{rbel:currentRequestAsString('$.body.my_cool_param')}" matches "client_id"
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/x-www-form-urlencoded.*"

  Scenario: Request with custom and default header
    Given TGR set default header "Content-Type" to "application/json"
    When Send POST request to "http://winstone/not_a_file" with
      | ${configured_param_name}   |
      | client_id                  |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.header.Content-Type')}" matches "application/json"

  @Ignore
  Scenario: Request with mutual TLS
    Given TGR set default TLS client certificate to "src/test/resources/rsaStoreWithChain.jks;gematik"
    When TGR send empty GET request to "https://winstone/not_a_file"
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.clientTlsCertificateChain.0.subject')}" matches ".*CN=authn.aktor.epa.telematik-test.*"
