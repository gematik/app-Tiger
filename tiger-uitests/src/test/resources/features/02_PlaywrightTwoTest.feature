Feature: Zweite Feature-Datei

  Scenario: Test zeige HTML
    Given TGR zeige HTML Notification:
    """
      <b>Fetter Text</b>
      <p>Mit details</p>
    """
    And TGR setze globale Variable "Test" auf "Dagmar"


  Scenario: Request for testing tooltips
    Given TGR set default header "key" to "value"
    When TGR send empty GET request to "http://httpbin/not_a_file" with headers:
      | foo | bar |
    Then TGR find last request to path ".*"
    And TGR print current request as rbel-tree
    And TGR assert "!{rbel:currentRequestAsString('$.header.foo')}" matches "bar"