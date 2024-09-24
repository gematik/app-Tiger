Feature: Zweite Feature-Datei

  Scenario: Test zeige HTML
    Given TGR zeige HTML Notification:
    """
      <b>Fetter Text</b>
      <p>Mit details</p>
    """
    And TGR setze globale Variable "Test" auf "Dagmar"


