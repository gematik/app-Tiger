@SingleBrowser
@FullTests @UnitTest
Feature: Check advanced input fields and sections for all server types

  Scenario: Check advanced fields in docker type
    Given Gerriet is on the homepage
    And he adds a "docker" node via welcome screen
    And he focuses on formular "docker_001"
    When he shows "Docker" tab
    And he clicks on global advanced icon
    Then he sees input field ".dockerOptions.entryPoint"
    When he clicks on global advanced icon
    Then he doesn't see field ".dockerOptions.entryPoint"

    When he shows "General" tab
    And he clicks on global advanced icon
    Then he sees input field "startupTimeoutSec"
    And he sees multiselect field "dependsUpon"
    When he clicks on global advanced icon
    Then he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"

    When he shows "Docker" tab
    And he clicks on global advanced icon
    Then he sees input field ".dockerOptions.entryPoint"
    When he clicks on global advanced icon
    Then he doesn't see field ".dockerOptions.entryPoint"

  Scenario Outline: Check advanced fields for compose, externalJar, excternalUrl type
    Given Gerriet is on the homepage
    And he adds a "<type>" node via welcome screen
    And he focuses on formular "<type>_001"
    And he shows "General" tab
    When he clicks on global advanced icon
    Then he sees input field "startupTimeoutSec"
    And he sees multiselect field "dependsUpon"
    When he clicks on global advanced icon
    Then he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"

    Examples:
    |type|
    |compose|
    |externalJar|
    |externalUrl|
    |tigerProxy |

  Scenario: Check advanced fields in tigerProxy type
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    When he focuses on formular "tigerProxy_001"
    When he shows "TigerProxy" tab
    And he clicks on global advanced icon
    Then he sees select field ".tigerProxyCfg.proxyProtocol"
    And he sees input field ".tigerProxyCfg.adminPort"
    And he sees select field ".tigerProxyCfg.proxyLogLevel"
    And he sees check field ".tigerProxyCfg.activateRbelParsing"
    And he sees check field ".tigerProxyCfg.activateRbelEndpoint"
    And he sees check field ".tigerProxyCfg.activateForwardAllLogging"
    And he sees check field ".tigerProxyCfg.activateAsn1Parsing"

    When he tests list ".tigerProxyCfg.proxyRoutes"
    And he opens complex list edit fields by clicking on add entry button
    Then he sees check field ".tigerProxyCfg.proxyRoutes.internalRoute"
    And he sees check field ".tigerProxyCfg.proxyRoutes.disableRbelLogging"

    And he sees section ".tigerProxyCfg.forwardToProxy"
    And he sees section ".tigerProxyCfg.trafficEndpoints"
    And he sees section ".tigerProxyCfg.modifications"
    And he sees section ".tigerProxyCfg.tls"

    When he unfolds section ".tigerProxyCfg.forwardToProxy"
    Then he sees check field "enableForwardProxy"
    And he sees input field ".tigerProxyCfg.forwardToProxy.hostname"
    And he sees input field ".tigerProxyCfg.forwardToProxy.port"
    And he sees select field ".tigerProxyCfg.forwardToProxy.type"

    When he unfolds section ".tigerProxyCfg.trafficEndpoints"
    Then he sees list field ".tigerProxyCfg.trafficEndpoints"
    And he sees check field ".tigerProxyCfg.skipTrafficEndpointsSubscription"
    And he sees input field ".tigerProxyCfg.connectionTimeoutInSeconds"
    And he sees input field ".tigerProxyCfg.stompClientBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.perMessageBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.rbelBufferSizeInMb"

    When he unfolds section ".tigerProxyCfg.modifications"
    Then he sees list field ".tigerProxyCfg.modifications"

    When he unfolds section ".tigerProxyCfg.tls"
    Then he sees input field ".tigerProxyCfg.tls.serverRootCa"
    And he sees input field ".tigerProxyCfg.tls.forwardMutualTlsIdentity"
    And he sees input field ".tigerProxyCfg.tls.serverIdentity"
    And he sees input field ".tigerProxyCfg.tls.domainName"
    And he sees list field ".tigerProxyCfg.tls.alternativeNames"
    And he sees list field ".tigerProxyCfg.tls.serverSslSuites"

    When he clicks on global advanced icon
    And he doesn't see field ".tigerProxyCfg.proxyProtocol"
    And he doesn't see field ".tigerProxyCfg.adminPort"
    And he doesn't see field ".tigerProxyCfg.proxyLogLevel"
    And he doesn't see field ".tigerProxyCfg.activateRbelParsing"
    And he doesn't see field ".tigerProxyCfg.activateRbelEndpoint"
    And he doesn't see field ".tigerProxyCfg.activateForwardAllLogging"
    And he doesn't see field ".tigerProxyCfg.activateAsn1Parsing"
    And he doesn't see field ".tigerProxyCfg.proxyRoutes.internalRoute"
    And he doesn't see field ".tigerProxyCfg.proxyRoutes.disableRbelLogging"
    And he doesn't see section ".tigerProxyCfg.forwardToProxy"
    And he doesn't see section ".tigerProxyCfg.trafficEndpoints"
    And he doesn't see section ".tigerProxyCfg.modifications"
    And he doesn't see section ".tigerProxyCfg.tls"


  Scenario: Check advanced fields in local proxy
    Given Gerriet is on the homepage
    And he adds a "externalUrl" node via welcome screen
    When he focuses on formular "local_tiger_proxy"
    When he shows "TigerProxy" tab
    And he clicks on global advanced icon
    Then he sees select field ".tigerProxyCfg.proxyProtocol"
    And he sees select field ".tigerProxyCfg.proxyLogLevel"
    And he sees check field ".tigerProxyCfg.activateRbelParsing"
    And he sees check field ".tigerProxyCfg.activateRbelEndpoint"
    And he sees check field ".tigerProxyCfg.activateForwardAllLogging"
    And he sees check field ".tigerProxyCfg.activateAsn1Parsing"

    When he tests list ".tigerProxyCfg.proxyRoutes"
    And he opens complex list edit fields by clicking on add entry button
    Then he sees check field ".tigerProxyCfg.proxyRoutes.internalRoute"
    And he sees check field ".tigerProxyCfg.proxyRoutes.disableRbelLogging"
    But he doesn't see field ".tigerProxyCfg.adminPort"

    And he sees section ".tigerProxyCfg.forwardToProxy"
    And he sees section ".tigerProxyCfg.trafficEndpoints"
    And he sees section ".tigerProxyCfg.modifications"
    And he sees section ".tigerProxyCfg.tls"

    When he unfolds section ".tigerProxyCfg.forwardToProxy"
    Then he sees check field "enableForwardProxy"
    And he sees input field ".tigerProxyCfg.forwardToProxy.hostname"
    And he sees input field ".tigerProxyCfg.forwardToProxy.port"
    And he sees select field ".tigerProxyCfg.forwardToProxy.type"

    When he unfolds section ".tigerProxyCfg.trafficEndpoints"
    Then he sees list field ".tigerProxyCfg.trafficEndpoints"
    And he sees check field ".tigerProxyCfg.skipTrafficEndpointsSubscription"
    And he sees input field ".tigerProxyCfg.connectionTimeoutInSeconds"
    And he sees input field ".tigerProxyCfg.stompClientBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.perMessageBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.rbelBufferSizeInMb"

    When he unfolds section ".tigerProxyCfg.modifications"
    Then he sees list field ".tigerProxyCfg.modifications"

    When he unfolds section ".tigerProxyCfg.tls"
    Then he sees input field ".tigerProxyCfg.tls.serverRootCa"
    And he sees input field ".tigerProxyCfg.tls.forwardMutualTlsIdentity"
    And he sees input field ".tigerProxyCfg.tls.serverIdentity"
    And he sees input field ".tigerProxyCfg.tls.domainName"
    And he sees list field ".tigerProxyCfg.tls.alternativeNames"
    And he sees list field ".tigerProxyCfg.tls.serverSslSuites"

    When he focuses on formular "local_tiger_proxy"
    And he clicks on global advanced icon
    Then he doesn't see field ".tigerProxyCfg.proxyProtocol"
    And he doesn't see field ".tigerProxyCfg.proxyLogLevel"
    And he doesn't see field ".tigerProxyCfg.activateRbelParsing"
    And he doesn't see field ".tigerProxyCfg.activateRbelEndpoint"
    And he doesn't see field ".tigerProxyCfg.activateForwardAllLogging"
    And he doesn't see field ".tigerProxyCfg.activateAsn1Parsing"

    And he doesn't see field ".tigerProxyCfg.proxyRoutes.internalRoute"
    And he doesn't see field ".tigerProxyCfg.proxyRoutes.disableRbelLogging"
    And he doesn't see section ".tigerProxyCfg.forwardToProxy"
    And he doesn't see section ".tigerProxyCfg.trafficEndpoints"
    And he doesn't see section ".tigerProxyCfg.modifications"
    And he doesn't see section ".tigerProxyCfg.tls"
