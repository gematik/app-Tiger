Feature: Test Tiger BDD

  Scenario: Simple first test
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"
    # Given TGR warte auf Abbruch

  Scenario: Simple first test
    Given TGR show banner "text2"
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"

