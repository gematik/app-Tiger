Feature: Test Tiger BDD

### Deaktiviert, dient der Veranschaulichung und Überprüfung des Demo-Modus
#  @Demo
#  Scenario: Demo modus
#    Given TGR zeige grünen Banner "Demo modus aktiv"
#    And TGR warte auf Abbruch
#    And TGR zeige grünen Text "Der Testbericht wird unter target/site/serenity/index.html erstellt"

  Background:
    Given TGR clear recorded messages

  Scenario Outline: JEXL Rbel Namespace Test
    Given TGR show banner "<txt>"
    When User requests the startpage
    Then TGR find request to path "/"
    And TGR print current request as rbel-tree
    Then TGR current response with attribute "$.body.html.head.link.href" matches "!{rbel:currentResponseAsString('$.body.html.head.link.href')}"

    Examples: We use this data only for testing data variant display in workflow ui, there is no deeper sense in it
      | txt   | txt2 | txt3| txt4| txt5|
      | text2 | 21   |31   |41   |51   |
      | text2 |22    |32   |42   |52   |

  Scenario: Simple first test
    Given TGR show banner "text2"
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"
    #Given TGR warte auf Abbruch

  Scenario: Test Find Last Request
    Given TGR show banner "text1"
    Then User requests "/classes" with parameter "foobar=1"
    Then User requests "/classes" with parameter "foobar=2"
    Then TGR find last request to path "/classes"
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree
    Then TGR current response with attribute "$.header.Location.foobar.value" matches "2"

  Scenario: Test find last request with parameters
    Given TGR show banner "text1"
    Then User requests "/classes" with parameter "foobar=1"
    Then User requests "/classes" with parameter "foobar=1&xyz=4"
    Then User requests "/classes" with parameter "foobar=2"
    Then TGR find last request to path "/classes" with "$.path.foobar.value" matching "1"
    And TGR print current request as rbel-tree
    And TGR print current response as rbel-tree
    Then TGR current response with attribute "$.header.Location.xyz.value" matches "4"
