@SingleBrowser
@FullTests @UiTests
Feature: Check sidebar drag n drop of nodes

  Scenario: Drag first below last sidebar item
    Given Gerriet is on the homepage
    And he adds a "docker" node via welcome screen
    And he adds a "compose" node via sidebar
    And he adds a "tigerProxy" node via sidebar
    And he adds a "externalJar" node via sidebar

    When he drags sidebar item "docker_001" below "externalJar_001"
    Then nodes are ordered "Local Tiger proxy,compose_001,tigerProxy_001,externalJar_001,docker_001"
    And formulars are ordered "local_tiger_proxy,compose_001,tigerProxy_001,externalJar_001,docker_001"

  # Scenario: Drag first to below second
    When he drags sidebar item "compose_001" below "tigerProxy_001"
    Then nodes are ordered "Local Tiger proxy,tigerProxy_001,compose_001,externalJar_001,docker_001"
    And formulars are ordered "local_tiger_proxy,tigerProxy_001,compose_001,externalJar_001,docker_001"

  # Scenario: Drag last above first
    When he drags sidebar item "docker_001" above "tigerProxy_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"
    And formulars are ordered "local_tiger_proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"

  # Scenario: Drag second below third
    When he drags sidebar item "tigerProxy_001" below "compose_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,compose_001,tigerProxy_001,externalJar_001"
    And formulars are ordered "local_tiger_proxy,docker_001,compose_001,tigerProxy_001,externalJar_001"

  # Scenario: Drag third above second
    When he drags sidebar item "tigerProxy_001" above "compose_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"
    And formulars are ordered "local_tiger_proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"

    # Scenario: dragging above local proxy makes it first
    # TODO behaviour is too flaky, replaying this manually shows that sometimes
    # moving is too fast to be recognized by the drag implementation
    # When he focuses on formular "compose_001"
    # When he drags sidebar item "compose_001" above "local_tiger_proxy"
    # Then nodes are ordered "Local Tiger proxy,compose_001,docker_001,tigerProxy_001,externalJar_001"
    # And formulars are ordered "local_tiger_proxy,compose_001,docker_001,tigerProxy_001,externalJar_001"

    # Scenario: Unable to drag local proxy
    When he tries to drag sidebar item "local_tiger_proxy" below "compose_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"
    And formulars are ordered "local_tiger_proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"

    # Scenario: Unable to drag sidebar item via label
    When he tries to drag sidebar item "compose_001" above "tigerProxy_001"
    Then nodes are ordered "Local Tiger proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"
    And formulars are ordered "local_tiger_proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"

    Then he saves test environment as "checkNodeUIRanking.yaml"


  Scenario: Check order of nodes is restored on reload of project
    Given Gerriet is on the homepage
    When he loads test environment from "checkNodeUIRanking.yaml"
    Then nodes are ordered "Local Tiger proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"
    And formulars are ordered "local_tiger_proxy,docker_001,tigerProxy_001,compose_001,externalJar_001"
