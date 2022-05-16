@SingleBrowser
@FullTests @UnitTest
Feature: Check input fields and sections for all server types

  Scenario: Check docker type
    Given Gerriet is on the homepage
    And he adds a "docker" node via welcome screen
    When he focuses on formular "docker_001"
    And he shows "Docker" tab
    Then he doesn't see field ".dockerOptions.serviceHealthchecks"
    But he sees check field ".dockerOptions.proxied"
    And he sees check field ".dockerOptions.oneShot"
    And he shows "General" tab
    And he sees input field "source"
    And he sees input field "version"

    And he sees tab link "General"
    And he sees tab link "Docker"
    And he sees tab link "PKI"
    And he sees tab link "Environment"
    And he sees tab link "URL Mappings"
    But he doesn't see tab link "External"
    And he doesn't see tab link "TigerProxy"

  Scenario: Check compose type
    Given Gerriet is on the homepage
    And he adds a "compose" node via welcome screen
    When he focuses on formular "compose_001"
    And he shows "Docker" tab
    Then he sees list field ".dockerOptions.serviceHealthchecks"
    But he doesn't see field ".dockerOptions.proxied"
    And he doesn't see field ".dockerOptions.oneShot"
    When he shows "General" tab
    And he sees list field "source"
    But he doesn't see field "version"

    And he sees tab link "General"
    And he sees tab link "Docker"
    And he sees tab link "PKI"
    And he sees tab link "Environment"
    And he sees tab link "URL Mappings"
    But he doesn't see tab link "External"
    And he doesn't see tab link "TigerProxy"

  Scenario: Check tigerProxy type
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    When he focuses on formular "tigerProxy_001"
    And he shows "TigerProxy" tab
    Then he sees input field ".tigerProxyCfg.proxyPort"
    And he sees select field ".tigerProxyCfg.proxiedServer"
    And he sees list field ".tigerProxyCfg.proxyRoutes"
    When he shows "General" tab
    Then he doesn't see field "source"
    And he doesn't see field "version"

    And he sees tab link "General"
    And he sees tab link "PKI"
    And he sees tab link "Environment"
    And he sees tab link "URL Mappings"
    And he sees tab link "TigerProxy"
    But he doesn't see tab link "Docker"
    And he doesn't see tab link "External"

  Scenario: Check externalJar type
    Given Gerriet is on the homepage
    And he adds a "externalJar" node via welcome screen
    When he focuses on formular "externalJar_001"
    And he shows "External" tab
    Then he sees input field ".externalJarOptions.workingDir"
    But he doesn't see field ".dockerOptions.serviceHealthchecks"
    And he doesn't see field ".externalJarOptions.options"
    And he doesn't see field ".externalJarOptions.arguments"
    When he unfolds section ".externalJarOptions.options"
    Then he sees list field ".externalJarOptions.options"
    When he unfolds section ".externalJarOptions.arguments"
    Then he sees list field ".externalJarOptions.arguments"
    When he shows "General" tab
    Then he sees input field "source"
    And he sees input field "healthcheckUrl"
    But he doesn't see field "version"

    And he sees tab link "General"
    And he sees tab link "PKI"
    And he sees tab link "Environment"
    And he sees tab link "URL Mappings"
    And he sees tab link "External"
    But he doesn't see tab link "Docker"
    And he doesn't see tab link "TigerProxy"

  Scenario: Check externalUrl type
    Given Gerriet is on the homepage
    And he adds a "externalUrl" node via welcome screen
    When he focuses on formular "externalUrl_001"
    And he shows "External" tab
    Then he doesn't see field ".externalJarOptions.workingDir"
    And he doesn't see field ".dockerOptions.serviceHealthchecks"
    And he doesn't see section ".externalJarOptions.options"
    And he doesn't see section ".externalJarOptions.arguments"
    When he shows "General" tab
    Then he sees input field "healthcheckUrl"
    And he sees input field "source"
    But he doesn't see field "version"

    And he sees tab link "General"
    And he sees tab link "PKI"
    And he sees tab link "Environment"
    And he sees tab link "URL Mappings"
    And he sees tab link "External"
    But he doesn't see tab link "Docker"
    And he doesn't see tab link "TigerProxy"

  Scenario: Check local proxy
    Given Gerriet is on the homepage
    And he adds a "externalUrl" node via welcome screen
    When he focuses on formular "local_tiger_proxy"
    Then he doesn't see field "source"
    And he doesn't see field "version"
    But he sees check field "localProxyActive"
    When he shows "TigerProxy" tab
    Then he sees input field ".tigerProxyCfg.proxyPort"
    And he sees list field ".tigerProxyCfg.proxyRoutes"
    But he doesn't see field ".tigerProxyCfg.proxiedServer"

    And he sees tab link "General"
    And he sees tab link "TigerProxy"
    But he doesn't see tab link "Docker"
    And he doesn't see tab link "PKI"
    And he doesn't see tab link "Environment"
    And he doesn't see tab link "URL Mappings"
    And he doesn't see tab link "External"

  Scenario Outline: Check PKI, Environment, URL Mappings tabs for all types but compose
    Given Gerriet is on the homepage
    And he adds a "<type>" node via welcome screen
    When he focuses on formular "<type>_001"
    And he shows "General" tab
    Then he sees input field "hostname"
    And he sees check field "active"
    When he shows "PKI" tab
    Then he sees list field ".pkiKeys"
    When he shows "Environment" tab
    Then he sees list field "<envfield1>"
    And he sees list field "<envfield2>"
    When he shows "URL Mappings" tab
    Then he sees list field "urlMappings"

    Examples:
      | type        | envfield1 | envfield2   |
      | docker      | exports   | environment |
      | tigerProxy  | exports   | environment |
      | externalJar | exports   | environment |
      | externalUrl | exports   | exports     |
    # we check for both sections in environment tab
    # except for externalUrl here no environment makes sense
    # thus checking twice for exports to keep it
    # with one outline scenario

  Scenario: Check PKI, Environment, URL Mappings tabs for compose
    Given Gerriet is on the homepage
    And he adds a "compose" node via welcome screen
    When he focuses on formular "compose_001"
    And he shows "General" tab
    #Then he sees input field "hostname" not shown on compose
    Then he sees check field "active"
    When he shows "PKI" tab
    Then he sees list field ".pkiKeys"
    When he shows "Environment" tab
    Then he sees list field "exports"
    And he sees list field "environment"
    When he shows "URL Mappings" tab
    Then he sees list field "urlMappings"
