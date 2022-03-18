Feature: Test Tiger BDD

### Deaktiviert, dient der Veranschaulichung und Überprüfung des Demo-Modus (und damit auch der MonitorUI)
#  @Demo
#  Scenario: Demo modus
#    Given TGR zeige grünen Banner "Demo modus aktiv"
#    And TGR warte auf Abbruch
#    And TGR zeige grünen Text "Der Testbericht wird unter target/site/serenity/index.html erstellt"

  Scenario: Simple first test
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"
