@AF-ID:AF_XY
@AF-ID:AF_YX
@AFO-ID:Blubber
Feature: Test Tiger BDD

  @AF-ID:AF_YZ
  @AF-ID:AF_ZY
  Scenario: Simple first test
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"

  @AF-ID:AF_XX
  Scenario: Simple second test
    Given TGR show banner "text2"
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"

  @AF-ID:AF_YX
  Scenario: Simple third test
    Given TGR show banner "text3"
    Given TGR show red banner "Starting Demo..."
    When User requests the startpage
    Then TGR find request to path "/"
    Then TGR current response with attribute "$.body.html.head.link.href" matches "jetty-dir.css"