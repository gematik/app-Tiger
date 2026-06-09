Feature: Zweite Feature-Datei

  Scenario: Test zeige HTML
    Given TGR zeige HTML Notification:
    """
     <b>Bold text</b>
     <p>with details</p>
    """
    And TGR setze globale Variable "Test" auf "Dagmar"


