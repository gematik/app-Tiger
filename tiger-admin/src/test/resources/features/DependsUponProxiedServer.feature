@FullTests @UiTests
@SingleBrowser
Feature: Check dependsUpon and proxiedServer fields are managed correctly

  Scenario: Add server and check server lists are updated
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    And he clicks on global advanced icon
    When he shows "General" tab
    Then he checks select field "dependsUpon" contains no entries
    When he shows "TigerProxy" tab
    Then he checks select field ".tigerProxyCfg.proxiedServer" contains no entries
    When he adds a "docker" node via sidebar
    And he shows "General" tab
    Then he checks select field "dependsUpon" contains "docker_001"
    When he shows "TigerProxy" tab
    Then he checks select field ".tigerProxyCfg.proxiedServer" contains ",docker_001"

  Scenario: Rename server and check server lists are updated and selection stays
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he adds a "docker" node via sidebar
    And he focuses on formular "tigerProxy_001"
    And he clicks on global advanced icon
    And he shows "General" tab
    And he selects entry "docker_001" in multi select field "dependsUpon"
    And he shows "TigerProxy" tab
    And he selects entry "docker_001" in select field ".tigerProxyCfg.proxiedServer"
    And he focuses on formular "docker_001"
    And he renames the node to "docker_222"
    And he focuses on formular "tigerProxy_001"
    And he shows "General" tab
    And he checks multi select field "dependsUpon" has entry "docker_222" selected
    And he shows "TigerProxy" tab
    And he checks select field ".tigerProxyCfg.proxiedServer" has entry "docker_222" selected

  Scenario: Delete server and check server lists are updated and selection is removed
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he adds a "docker" node via sidebar
    And he focuses on formular "tigerProxy_001"
    And he clicks on global advanced icon
    And he shows "General" tab
    And he selects entry "docker_001" in multi select field "dependsUpon"
    And he shows "TigerProxy" tab
    And he selects entry "docker_001" in select field ".tigerProxyCfg.proxiedServer"
    And he deletes node "docker_001"
    And he confirms modal
    And he shows "General" tab
    And he checks select field "dependsUpon" contains no entries
    And he shows "TigerProxy" tab
    And he checks select field ".tigerProxyCfg.proxiedServer" contains no entries
    And he shows "General" tab
    And he checks multi select field "dependsUpon" has no entry selected
    And he shows "TigerProxy" tab
    And he checks select field ".tigerProxyCfg.proxiedServer" has entry "" selected
