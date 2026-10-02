Feature: Zweite Feature-Datei

  Scenario: Test zeige HTML
    And TGR setze globale Variable "Test" auf "Dagmar"

  # Last scenario of the whole fixture on purpose: the "selected/last scenario only" Rbel log
  # toggle (TGR-2300) resolves to the last scenario that actually ran, so this needs to be a
  # scenario with real Rbel traffic to test that the toggle actually narrows the log down.
  Scenario: Simple Get Request for scoped scenario test
    When TGR send empty GET request to "http://httpbin/get"
    Then TGR find last request to path "/get"



