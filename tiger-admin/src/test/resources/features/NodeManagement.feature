@FullTests @UiTests
Feature: Node management

  Scenario: add and delete server nodes
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    And he adds a "tigerProxy" node via sidebar
    And he adds a "externalJar" node via sidebar
    And he adds a "externalUrl" node via sidebar
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,tigerProxy_001,externalJar_001,externalUrl_001"
    And formulars are ordered "local_proxy,docker_001,compose_001,tigerProxy_001,externalJar_001,externalUrl_001"

    # Scenario: remove server nodes
    When he deletes node "docker_001"
    And he confirms modal
    Then nodes are ordered "Local Tiger proxy,compose_001,tigerProxy_001,externalJar_001,externalUrl_001"
    And formulars are ordered "local_proxy,compose_001,tigerProxy_001,externalJar_001,externalUrl_001"

    When he deletes node "externalJar_001"
    And he confirms modal
    Then nodes are ordered "Local Tiger proxy,compose_001,tigerProxy_001,externalUrl_001"
    And formulars are ordered "local_proxy,compose_001,tigerProxy_001,externalUrl_001"

    When he deletes node "tigerProxy_001"
    And he confirms modal
    Then nodes are ordered "Local Tiger proxy,compose_001,externalUrl_001"
    And formulars are ordered "local_proxy,compose_001,externalUrl_001"

    When he deletes node "externalUrl_001"
    And he confirms modal
    Then nodes are ordered "Local Tiger proxy,compose_001"
    And formulars are ordered "local_proxy,compose_001"

    When he deletes node "compose_001"
    And he confirms modal
    Then nodes are ordered "Local Tiger proxy"
    And formulars are ordered "local_proxy"

  Scenario: add server type twice and check index in server key is increased
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001"
    And formulars are ordered "local_proxy,docker_001,compose_001"
    When he adds a "compose" node via sidebar
    And he adds a "compose" node via sidebar
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,compose_002,compose_003"
    And formulars are ordered "local_proxy,docker_001,compose_001,compose_002,compose_003"

  Scenario: rename node
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001"
    And formulars are ordered "local_proxy,docker_001,compose_001"
    When he focuses on formular "compose_001"
    And he renames the node to "compose_222"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_222"
    And formulars are ordered "local_proxy,docker_001,compose_222"

  Scenario: rename node with spaces
    Given Gerriet is on the homepage
    And he adds a "compose" node via welcome screen
    Then nodes are ordered "Local Tiger proxy,compose_001"
    And formulars are ordered "local_proxy,compose_001"
    When he focuses on formular "compose_001"
    And he renames the node to "co mpose_222"
    Then nodes are ordered "Local Tiger proxy,co_mpose_222"
    And formulars are ordered "local_proxy,co_mpose_222"
    And he sees snack starting with "No SPACES allowed in server key!"
    And he closes open snack

  Scenario: rename node with invalid characters
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,docker_001"
    When he focuses on formular "docker_001"
    And he renames the node to "do:cker_222"
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,do:cker_222"
    And he sees snack starting with "Only ASCII characters, digits and underscore allowed"
    And he closes open snack
    When he renames the node to "do$cker_222"
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,do$cker_222"
    And he sees snack starting with "Only ASCII characters, digits and underscore allowed"
    And he closes open snack
    When he renames the node to "döckär_222"
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,döckär_222"
    And he sees snack starting with "Only ASCII characters, digits and underscore allowed"
    And he closes open snack
    When he enters "docker_666" as new name for the node
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,docker_666"
    And he aborts node renaming pressing ESC
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,docker_001"
    When he renames the node to ""
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,"
    And he sees snack starting with "Empty node name not allowed!"
    And he closes open snack

  Scenario: add server and make new testenv
    Given Gerriet is on the homepage
    And he adds a "docker" node via welcome screen
    When he creates a new test environment
    And he dismisses confirm modal
    Then nodes are ordered "Local Tiger proxy,docker_001"
    And formulars are ordered "local_proxy,docker_001"
    When he creates a new test environment
    And he confirms modal
    Then he sees welcome screen
    And he doesn't see sidebar header

  Scenario: duplicate node simple increment
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001"
    And formulars are ordered "local_proxy,docker_001,compose_001"
    When he focuses on formular "docker_001"
    And he duplicates node "docker_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,docker_002"
    And formulars are ordered "local_proxy,docker_001,compose_001,docker_002"
    When he duplicates node "docker_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,docker_002,docker_003"
    And formulars are ordered "local_proxy,docker_001,compose_001,docker_002,docker_003"

  Scenario: duplicate node no dash
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    When he focuses on formular "docker_001"
    And he renames the node to "docker"
    Then nodes are ordered "Local Tiger proxy,docker,compose_001"
    And formulars are ordered "local_proxy,docker,compose_001"
    When he focuses on formular "docker"
    And he duplicates node "docker"
    Then nodes are ordered "Local Tiger proxy,docker,compose_001,docker_001"
    And formulars are ordered "local_proxy,docker,compose_001,docker_001"
    When he duplicates node "docker_001"
    Then nodes are ordered "Local Tiger proxy,docker,compose_001,docker_001,docker_002"
    And formulars are ordered "local_proxy,docker,compose_001,docker_001,docker_002"

  Scenario: duplicate node already existing
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    And he adds a "docker" node via sidebar
    When he focuses on formular "docker_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,docker_002"
    And formulars are ordered "local_proxy,docker_001,compose_001,docker_002"
    When he duplicates node "docker_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,docker_002,docker_003"
    And formulars are ordered "local_proxy,docker_001,compose_001,docker_002,docker_003"

  Scenario: duplicate node already existing in between
    Given Gerriet is on the homepage
    When he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    And he adds a "docker" node via sidebar
    And he adds a "docker" node via sidebar
    When he focuses on formular "docker_002"
    And he renames the node to "dockerA_002"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,dockerA_002,docker_003"
    And formulars are ordered "local_proxy,docker_001,compose_001,dockerA_002,docker_003"
    When he duplicates node "docker_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,dockerA_002,docker_003,docker_002"
    And formulars are ordered "local_proxy,docker_001,compose_001,dockerA_002,docker_003,docker_002"
