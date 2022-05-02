Feature: Test Tiger BDD

### Deaktiviert, dient der Veranschaulichung und Überprüfung des Demo-Modus (und damit auch der MonitorUI)
#  @Demo
#  Scenario: Demo modus
#    Given TGR zeige grünen Banner "Demo modus aktiv"
#    And TGR warte auf Abbruch
#    And TGR zeige grünen Text "Der Testbericht wird unter target/site/serenity/index.html erstellt"

  Background:
    Given TGR show green banner "GRÜN!"

  Scenario Outline: JEXL Rbel Namespace Test
    Given TGR show banner "<txt>"
    When User requests the startpage
    Then TGR find request to path "/"
    And TGR print current request as rbel-tree
    Then TGR current response with attribute "$.body.html.head.link.href" matches "!{rbel:lastResponseAsString('$.body.html.head.link.href')}"

    Examples:
      | txt   |
      | text2 |
      | text2 |

  Scenario: Simple first test
    Given TGR show banner "text2"
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"
    #Given TGR warte auf Abbruch
