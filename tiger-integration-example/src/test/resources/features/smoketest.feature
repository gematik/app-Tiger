Feature: Test Tiger BDD

### Deaktiviert, dient der Veranschaulichung und Überprüfung des Demo-Modus
#  @Demo
#  Scenario: Demo modus
#    Given TGR zeige grünen Banner "Demo modus aktiv"
#    And TGR warte auf Abbruch
#    And TGR zeige grünen Text "Der Testbericht wird unter target/site/serenity/index.html erstellt"

  Scenario: Test show HTML
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    And TGR send empty GET request to "https://www.google.com"
    Given TGR show HTML Notification:
    """
      <b>Fetter Text</b>
      <p>Mit details</p>
    """
    And TGR show HTML Notification:
    """
      <span style="color: red">This should be red.</span>
    """

  @Ignore
  Scenario: Dieses Scenario sollte ignoriert werden
    Given TGR show banner "Das solltest du nicht sehen"
    Then Hier erwarte ich einen Fehler
