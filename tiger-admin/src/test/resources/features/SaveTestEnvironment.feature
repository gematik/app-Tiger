@SingleBrowser
@FullTests @UiTests
Feature: Save test environments

  Scenario: save test environment by hitting Enter
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    Then he saves test environment as "newTestEnv.yaml" using Enter
    Then he verifies saved file "newTestEnv.yaml" contains
    """
    servers:
      docker_001:
        hostname: docker_001
        uiRank: 1
        type: docker
    """

  Scenario Outline: save test environment with a wrong name
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    Then he doesn't save test environment as "<saveName>"
    Examples:
      | saveName     |
      | ' '          |
      | .yaml        |
      | saveName     |
      | saveName.txt |

  Scenario: save test environment with Save As button
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    And he adds a "docker" node via sidebar
    Then he saves test environment as "newTestEnv.yaml"
    Then he verifies saved file "newTestEnv.yaml" contains
    """
    servers:
      docker_002:
        hostname: docker_002
        uiRank: 3
        type: docker
      docker_001:
        hostname: docker_001
        uiRank: 1
        type: docker
      compose_001:
        uiRank: 2
        type: compose
    """

  Scenario: save test environment with all nodes and their parameters
    Given Gerriet is on the homepage

    # Scenario: add parameters to a docker node
    When he adds a "docker" node via welcome screen
    Then he focuses on formular "docker_001"
    And he shows "URL Mappings" tab
    And he tests list "urlMappings"
    When he adds list item "entry1"
    Then he checks list item in row 1 has value "entry1"

    # Scenario: add parameters to a compose node
    And he adds a "compose" node via sidebar
    Then he focuses on formular "compose_001"
    And he shows "Environment" tab
    And he tests list "environment"
    When he adds list item "entry1"
    Then he checks list item in row 1 has value "entry1"

    # Scenario: add parameters to an externalUrl node
    When he adds a "externalUrl" node via sidebar
    Then he focuses on formular "externalUrl_001"
    And he shows "PKI" tab
    And he tests list ".pkiKeys"
    Then he adds complex list item
    """
      pem: Pem1
      id: 123
      type: Key

    """
    Then he checks list item in row 1 has value "123(Key)"

    # Scenario: add parameters to a tigerProxy node
    And he adds a "tigerProxy" node via sidebar
    Then he focuses on formular "tigerProxy_001"
    And he shows "TigerProxy" tab
    And he tests list ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then he adds complex list item
    """
      id: entry1
      from: from1
      to: to1
      basicAuth.username: user1
      basicAuth.password: pwd1

    """
    Then he checks list item in row 1 has value "entry1: [user1@pwd1] from1 ↦ to1"

#    # Scenario: he adds parameters to an external Jar node
    And he adds a "externalJar" node via sidebar
    And he focuses on formular "externalJar_001"
    And he shows "External" tab
    And he unfolds section ".externalJarOptions.options"
    And he tests list ".externalJarOptions.options"
    When he adds list item "entry1"
    Then he checks list item in row 1 has value "entry1"

#    TODO: TGR-411: PKI Keys are not saved correctly in the files
#    TODO: TGR-308: Keep order of nodes from ui persistent
    Then he saves test environment as "newTestEnv.yaml"
    Then he verifies saved file "newTestEnv.yaml" contains
    """
    servers:
      externalUrl_001:
        hostname: externalUrl_001
        uiRank: 3
        pkiKeys:
        - pem: Pem1
          id: '123'
          type: Key
        type: externalUrl
      docker_001:
        hostname: docker_001
        uiRank: 1
        type: docker
        urlMappings:
        - entry1
      externalJar_001:
        hostname: externalJar_001
        uiRank: 5
        type: externalJar
        externalJarOptions:
          options:
          - entry1
      compose_001:
        environment:
        - entry1
        uiRank: 2
        type: compose
      tigerProxy_001:
        tigerProxyCfg:
          proxyCfg:
            proxyRoutes:
            - basicAuth:
                password: pwd1
                username: user1
              from: from1
              id: entry1
              to: to1
          proxyProtocol: http
        hostname: tigerProxy_001
        uiRank: 4
        type: tigerProxy
    """

  Scenario: cancel saving of test environments
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    Then he cancels the saving of the file with the name "newTestEnv.yaml"
