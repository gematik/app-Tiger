Feature: Testing scenario outlines

  Background:
    Given TGR clear recorded messages

  Scenario Outline: Hello <HelloWhat>
    When TGR show banner "HELLO <HelloWhat>"
    Then TGR set global variable "hello" to "<HelloWhat>"
    And TGR print variable "hello"
    Examples:
      | HelloWhat  |
      | World      |
      | Universe   |
      | Everything |

  Scenario: Request a non existing url
    When TGR send empty GET request to "http://www.this_is_not_a_real_url_blablabla.com"

  Scenario: This one just hello
    When TGR show banner "HELLO"