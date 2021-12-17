@FullTests @UnitTest
Feature: Check advanced input fields and sections for all server types

  Scenario: Check advanced fields in docker type
    Given Gerriet is on the homepage
    And he adds a "docker" node via welcome screen
    And he focuses on formular "docker_001"
    When he clicks on global advanced icon
    Then he sees input field "startupTimeoutSec"
    And he sees select field "dependsUpon"
    And he sees input field ".dockerOptions.entryPoint"
    When he clicks on global advanced icon
    Then he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"
    And he doesn't see field ".dockerOptions.entryPoint"
    When he clicks on advanced icon in section "node-settings"
    Then he sees input field "startupTimeoutSec"
    And he sees select field "dependsUpon"
    But he doesn't see field ".dockerOptions.entryPoint"
    When he clicks on advanced icon in section "node-settings"
    And he clicks on advanced icon in section ".dockerOptions.dockerSettings"
    Then he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"
    But he sees input field ".dockerOptions.entryPoint"
    When he clicks on advanced icon in section ".dockerOptions.dockerSettings"
    Then he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"
    And he doesn't see field ".dockerOptions.entryPoint"

  Scenario Outline: Check advanced fields for compose, externalJar, excternalUrl type
    Given Gerriet is on the homepage
    And he adds a "<type>" node via welcome screen
    And he focuses on formular "<type>_001"
    When he clicks on advanced icon in section "node-settings"
    Then he sees input field "startupTimeoutSec"
    And he sees select field "dependsUpon"
    When he clicks on advanced icon in section "node-settings"
    Then he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"

    Examples:
    |type|
    |compose|
    |externalJar|
    |externalUrl|

  Scenario: Check advanced fields in tigerProxy type
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    When he focuses on formular "tigerProxy_001"
    When he clicks on global advanced icon
    Then he sees input field "startupTimeoutSec"
    And he sees select field "dependsUpon"
    And he sees select field ".tigerProxyCfg.proxyProtocol"
    And he sees input field ".tigerProxyCfg.serverPort"
    And he sees select field ".tigerProxyCfg.proxyCfg.proxyLogLevel"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateRbelParsing"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateRbelEndpoint"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateForwardAllLogging"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateAsn1Parsing"
    And he sees check field ".tigerProxyCfg.proxyCfg.proxyRoutes.internalRoute"
    And he sees check field ".tigerProxyCfg.proxyCfg.proxyRoutes.disableRbelLogging"

    And he sees section ".tigerProxyCfg.proxyCfg.forwardToProxy"
    And he sees section ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    And he sees section ".tigerProxyCfg.proxyCfg.modifications"
    And he sees section ".tigerProxyCfg.proxyCfg.tls"

    When he unfolds section ".tigerProxyCfg.proxyCfg.forwardToProxy"
    Then he sees check field "enableForwardProxy"
    And he sees input field ".tigerProxyCfg.proxyCfg.forwardToProxy.hostname"
    And he sees input field ".tigerProxyCfg.proxyCfg.forwardToProxy.port"
    And he sees select field ".tigerProxyCfg.proxyCfg.forwardToProxy.type"

    When he unfolds section ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    Then he sees list field ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    And he sees check field ".tigerProxyCfg.proxyCfg.skipTrafficEndpointsSubscription"
    And he sees input field ".tigerProxyCfg.proxyCfg.connectionTimeoutInSeconds"
    And he sees input field ".tigerProxyCfg.proxyCfg.stompClientBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.proxyCfg.perMessageBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.proxyCfg.rbelBufferSizeInMb"

    When he unfolds section ".tigerProxyCfg.proxyCfg.modifications"
    Then he sees list field ".tigerProxyCfg.proxyCfg.modifications"

    When he unfolds section ".tigerProxyCfg.proxyCfg.tls"
    Then he sees input field ".tigerProxyCfg.proxyCfg.tls.serverRootCa.fileLoadingInformation"
    And he sees input field ".tigerProxyCfg.proxyCfg.tls.forwardMutualTlsIdentity.fileLoadingInformation"
    And he sees input field ".tigerProxyCfg.proxyCfg.tls.serverIdentity.fileLoadingInformation"
    And he sees input field ".tigerProxyCfg.proxyCfg.tls.domainName"
    And he sees list field ".tigerProxyCfg.proxyCfg.tls.alternativeNames"
    And he sees list field ".tigerProxyCfg.proxyCfg.tls.serverSslSuites"

    When he clicks on global advanced icon
    Then he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"
    And he doesn't see field ".tigerProxyCfg.proxyProtocol"
    And he doesn't see field ".tigerProxyCfg.serverPort"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.proxyLogLevel"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateRbelParsing"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateRbelEndpoint"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateForwardAllLogging"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateAsn1Parsing"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.proxyRoutes.internalRoute"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.proxyRoutes.disableRbelLogging"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.forwardToProxy"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.modifications"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.tls"


  Scenario: Check advanced fields in local proxy
    Given Gerriet is on the homepage
    And he adds a "externalUrl" node via welcome screen
    When he focuses on formular "local_proxy"
    When he clicks on global advanced icon
    And he sees select field ".tigerProxyCfg.proxyProtocol"
    But he doesn't see field ".tigerProxyCfg.serverPort"
    And he sees select field ".tigerProxyCfg.proxyCfg.proxyLogLevel"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateRbelParsing"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateRbelEndpoint"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateForwardAllLogging"
    And he sees check field ".tigerProxyCfg.proxyCfg.activateAsn1Parsing"
    And he sees check field ".tigerProxyCfg.proxyCfg.proxyRoutes.internalRoute"
    And he sees check field ".tigerProxyCfg.proxyCfg.proxyRoutes.disableRbelLogging"
    But he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"

    And he sees section ".tigerProxyCfg.proxyCfg.forwardToProxy"
    And he sees section ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    And he sees section ".tigerProxyCfg.proxyCfg.modifications"
    And he sees section ".tigerProxyCfg.proxyCfg.tls"

    When he unfolds section ".tigerProxyCfg.proxyCfg.forwardToProxy"
    Then he sees check field "enableForwardProxy"
    And he sees input field ".tigerProxyCfg.proxyCfg.forwardToProxy.hostname"
    And he sees input field ".tigerProxyCfg.proxyCfg.forwardToProxy.port"
    And he sees select field ".tigerProxyCfg.proxyCfg.forwardToProxy.type"

    When he unfolds section ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    Then he sees list field ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    And he sees check field ".tigerProxyCfg.proxyCfg.skipTrafficEndpointsSubscription"
    And he sees input field ".tigerProxyCfg.proxyCfg.connectionTimeoutInSeconds"
    And he sees input field ".tigerProxyCfg.proxyCfg.stompClientBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.proxyCfg.perMessageBufferSizeInMb"
    And he sees input field ".tigerProxyCfg.proxyCfg.rbelBufferSizeInMb"

    When he unfolds section ".tigerProxyCfg.proxyCfg.modifications"
    Then he sees list field ".tigerProxyCfg.proxyCfg.modifications"

    When he unfolds section ".tigerProxyCfg.proxyCfg.tls"
    Then he sees input field ".tigerProxyCfg.proxyCfg.tls.serverRootCa.fileLoadingInformation"
    And he sees input field ".tigerProxyCfg.proxyCfg.tls.forwardMutualTlsIdentity.fileLoadingInformation"
    And he sees input field ".tigerProxyCfg.proxyCfg.tls.serverIdentity.fileLoadingInformation"
    And he sees input field ".tigerProxyCfg.proxyCfg.tls.domainName"
    And he sees list field ".tigerProxyCfg.proxyCfg.tls.alternativeNames"
    And he sees list field ".tigerProxyCfg.proxyCfg.tls.serverSslSuites"

    When he focuses on formular "local_proxy"
    And he clicks on global advanced icon
    Then he doesn't see field ".tigerProxyCfg.proxyProtocol"
    And he doesn't see field "startupTimeoutSec"
    And he doesn't see field "dependsUpon"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.proxyLogLevel"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateRbelParsing"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateRbelEndpoint"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateForwardAllLogging"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.activateAsn1Parsing"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.proxyRoutes.internalRoute"
    And he doesn't see field ".tigerProxyCfg.proxyCfg.proxyRoutes.disableRbelLogging"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.forwardToProxy"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.trafficEndpoints"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.modifications"
    And he doesn't see section ".tigerProxyCfg.proxyCfg.tls"
