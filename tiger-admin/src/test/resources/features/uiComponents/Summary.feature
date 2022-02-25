@SingleBrowser
@FullTests @UnitTest
Feature: Summary feature

  Scenario: create tigerproxy node and check route summary is shown
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    When he tests list ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he folds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then the summary of section ".tigerProxyCfg.proxyCfg.proxyRoutes" matches
    """
      No entries
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he adds complex list item
    """
      id: id1
      from: from1
      to: to1
      basicAuth.username: user1
      basicAuth.password: pwd1
    """
    And he folds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then the summary of section ".tigerProxyCfg.proxyCfg.proxyRoutes" matches
    """
      id1: [user1@pwd1] from1 ↦ to1
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he adds complex list item
    """
      id: id2
      from: from2
      to: to2
      basicAuth.username: user2
      basicAuth.password: pwd2
    """
    And he folds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then the summary of section ".tigerProxyCfg.proxyCfg.proxyRoutes" matches
    """
      id1: [user1@pwd1] from1 ↦ to1,
      id2: [user2@pwd2] from2 ↦ to2
    """

  Scenario: modify route section entry and check summary is adapted
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    When he tests list ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he adds complex list item
    """
      id: id1
      from: from1
      to: to1
      basicAuth.username: user1
      basicAuth.password: pwd1
    """
    And he folds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then the summary of section ".tigerProxyCfg.proxyCfg.proxyRoutes" matches
    """
      id1: [user1@pwd1] from1 ↦ to1
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he sets active complex list item to
    """
      id: id3
    """
    And he folds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then the summary of section ".tigerProxyCfg.proxyCfg.proxyRoutes" matches
    """
      id3: [user1@pwd1] from1 ↦ to1
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he sets active complex list item to
    """
      from: from3
    """
    And he folds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then the summary of section ".tigerProxyCfg.proxyCfg.proxyRoutes" matches
    """
      id3: [user1@pwd1] from3 ↦ to1
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he sets active complex list item to
    """
      basicAuth.username: user3
    """
    And he folds section ".tigerProxyCfg.proxyCfg.proxyRoutes"
    Then the summary of section ".tigerProxyCfg.proxyCfg.proxyRoutes" matches
    """
      id3: [user3@pwd1] from3 ↦ to1
    """

  Scenario: modify tls section and check summary is adapted
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    And he clicks on global advanced icon
    Then the summary of section ".tigerProxyCfg.proxyCfg.tls" matches
    """
      localhost
      Additional Names:
      No entries
      Server SSL Suites:
      No entries
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.tls"
    And he tests list ".tigerProxyCfg.proxyCfg.tls.alternativeNames"
    And he adds list item "altname1"
    And he folds section ".tigerProxyCfg.proxyCfg.tls"
    Then the summary of section ".tigerProxyCfg.proxyCfg.tls" matches
    """
      localhost
      Additional Names:
      altname1
      Server SSL Suites:
      No entries
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.tls"
    And he tests list ".tigerProxyCfg.proxyCfg.tls.serverSslSuites"
    And he adds list item "sslSuite1"
    And he folds section ".tigerProxyCfg.proxyCfg.tls"
    Then the summary of section ".tigerProxyCfg.proxyCfg.tls" matches
    """
      localhost
      Additional Names:
      altname1
      Server SSL Suites:
      sslSuite1
    """
    When he unfolds section ".tigerProxyCfg.proxyCfg.tls"
    And he enters "rootca1" into field ".tigerProxyCfg.proxyCfg.tls.serverRootCa"
    And he folds section ".tigerProxyCfg.proxyCfg.tls"
    Then the summary of section ".tigerProxyCfg.proxyCfg.tls" matches
    """
      localhost
      RootCa rootca1
      Additional Names:
      altname1
      Server SSL Suites:
      sslSuite1
    """
