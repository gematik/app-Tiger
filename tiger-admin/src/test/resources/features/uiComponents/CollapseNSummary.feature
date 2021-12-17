@FullTests @UnitTest
Feature: Check collapsing sections

  Scenario: Check collapse docker node
    Given Gerriet is on the homepage
    And he adds a "docker" node via welcome screen
    When he focuses on formular "docker_001"
    And he collapses the node
    Then he doesn't see section "node-settings"
    And he doesn't see section "source"
    And he doesn't see section ".dockerOptions.dockerSettings"
    And he doesn't see tab link "Docker"
    And he doesn't see tab link "PKI"
    And he doesn't see tab link "Environment"
    And he doesn't see tab link "URL Mappings"

  Scenario: Check collapse compose node
    Given Gerriet is on the homepage
    And he adds a "compose" node via welcome screen
    When he focuses on formular "compose_001"
    And he collapses the node
    Then he doesn't see section "node-settings"
    And he doesn't see section "source"
    And he doesn't see section ".dockerOptions.dockerSettings"
    And he doesn't see tab link "Docker"
    And he doesn't see tab link "PKI"
    And he doesn't see tab link "Environment"
    And he doesn't see tab link "URL Mappings"

  Scenario: Check collapse tigerProxy node
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    When he focuses on formular "tigerProxy_001"
    And he collapses the node
    Then he doesn't see section "node-settings"
    And he doesn't see section "source"
    And he doesn't see section ".tigerProxyCfg"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he doesn't see tab link "TigerProxy"
    And he doesn't see tab link "External"
    And he doesn't see tab link "PKI"
    And he doesn't see tab link "Environment"
    And he doesn't see tab link "URL Mappings"

  Scenario: Check collapse externalJar node
    Given Gerriet is on the homepage
    And he adds a "externalJar" node via welcome screen
    When he focuses on formular "externalJar_001"
    And he collapses the node
    Then he doesn't see section "node-settings"
    And he doesn't see section "source"
    And he doesn't see section ".externalJarOptions.externalSettings"
    And he doesn't see section ".externalJarOptions.options"
    And he doesn't see section ".externalJarOptions.arguments"
    And he doesn't see tab link "External"
    And he doesn't see tab link "PKI"
    And he doesn't see tab link "Environment"
    And he doesn't see tab link "URL Mappings"

  Scenario: Check collapse externalUrl node
    Given Gerriet is on the homepage
    And he adds a "externalUrl" node via welcome screen
    When he focuses on formular "externalUrl_001"
    And he collapses the node
    Then he doesn't see section "node-settings"
    And he doesn't see section "source"
    And he doesn't see section ".externalJarOptions.externalSettings"
    And he doesn't see tab link "External"
    And he doesn't see tab link "PKI"
    And he doesn't see tab link "Environment"
    And he doesn't see tab link "URL Mappings"

  Scenario: Check collapse local proxy
    Given Gerriet is on the homepage
    And he adds a "externalUrl" node via welcome screen
    When he focuses on formular "local_proxy"
    And he collapses the node
    And he doesn't see section ".tigerProxyCfg"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he doesn't see tab link "TigerProxy"
