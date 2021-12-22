@FullTests @UnitTest
Feature: Test functionality of complex lists

  Scenario: Test complex list item management
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    And he tests list ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he checks list add button is enabled

  #Scenario: Adding and inserting an entry to list
    When he adds complex list item
    """
      id: entry1
      from: from1
      to: to1
      basicAuth.username: user1
      basicAuth.password: pwd1
    """
    Then he checks list item in row 1 has value "entry1: [user1@pwd1] from1 ↦ to1"

  # Scenario: Inserting an entry to list before active item
    When he selects list item in row 1
    And  he adds complex list item
    """
      id: entry2
      from: from2
      to: to2
      basicAuth.username: user2
      basicAuth.password: pwd2
    """
    And he checks list item in row 1 has value "entry1: [user1@pwd1] from1 ↦ to1"
    Then he checks list item in row 2 has value "entry2: [user2@pwd2] from2 ↦ to2"

  # Scenario: Editing an existing entry
    Given he selects list item in row 2
    When he sets active complex list item to
    """
      id: entry3
      from: from3
      to: to3
      basicAuth.username: user3
      basicAuth.password: pwd3
    """
    Then he checks list item in row 2 has value "entry3: [user3@pwd3] from3 ↦ to3"

  # Scenario: Abort editing an entry via ESC
    Given he selects list item in row 1
    When he enters values to the active complex list item
    """
      id: entry4
      from: from4
      to: to4
      basicAuth.username: user4
      basicAuth.password: pwd4
    """
    And he selects list item in row 2
    And he closes open snack
    And he selects list item in row 1
    Then complex list item field "id" has value "entry1"
    And complex list item field "from" has value "from1"
    And complex list item field "to" has value "to1"
    And complex list item field "basicAuth.username" has value "user1"
    And complex list item field "basicAuth.password" has value "pwd1"
    And he checks list item in row 1 has value "entry1: [user1@pwd1] from1 ↦ to1"

  # Scenario: Abort editing an entry via add button
    When he selects list item in row 1
    And he enters values to the active complex list item
    """
      id: entry4
      from: from4
      to: to4
      basicAuth.username: user4
      basicAuth.password: pwd4
    """
    And he adds complex list item
     """
      id: entry5
      from: from5
      to: to5
      basicAuth.username: user5
      basicAuth.password: pwd5
    """
    And he closes open snack
    Then he checks list item in row 1 has value "entry1: [user1@pwd1] from1 ↦ to1"
    And he checks list item in row 2 has value "entry5: [user5@pwd5] from5 ↦ to5"

  # TODO add snack detection?



  # Scenario: Abort editing an entry via select other item
    When he selects list item in row 2
    And he enters values to the active complex list item
    """
      id: entry6
      from: from6
      to: to6
      basicAuth.username: user6
      basicAuth.password: pwd6
    """
    And he selects list item in row 1
    And he closes open snack
    Then he checks list item in row 2 has value "entry5: [user5@pwd5] from5 ↦ to5"

  # Scenario: Abort editing an entry via delete button
    When he selects list item in row 1
    And he enters values to the active complex list item
    """
      id: entry6
      from: from6
      to: to6
      basicAuth.username: user6
      basicAuth.password: pwd6
    """
    And he deletes active list item
    Then he checks list item in row 1 has value "entry5: [user5@pwd5] from5 ↦ to5"

  # Scenario: Deleting an existing entry
    Given he checks list length is 2
    And he selects list item in row 1
    When he deletes active list item
    And he checks active list item is in row 1
    And he checks list item in row 1 has value "entry3: [user3@pwd3] from3 ↦ to3"
    And he checks list length is 1

  # Scenario: check if i delete last entry in list then previous prelast is active
    Given he adds complex list item
     """
      id: entry7
      from: from7
      to: to7
      basicAuth.username: user7
      basicAuth.password: pwd7
    """
    And he adds complex list item
     """
      id: entry8
      from: from8
      to: to8
      basicAuth.username: user8
      basicAuth.password: pwd8
    """
    And he selects list item in row 3
    When he deletes active list item
    Then he checks active list item is in row 2

  # Scenario: Deleting last remaining entries
    When he deletes active list item
    And he deletes active list item
    Then he checks list length is 0

  Scenario: Drag items in complex list
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    And he tests list ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he adds complex list item
      """
      id: drag1
      """
    And he adds complex list item
      """
      id: drag2
      """
    And he adds complex list item
      """
      id: drag3
      """
    And he adds complex list item
      """
      id: drag4
      """

  # Scenario: drag first after last pos
    Given he scrolls to row 4 in list
    When he drags list item in row 1 below item in row 4
    Then he checks list item in row 4 has value "drag1:"
    And he checks list item in row 1 has value "drag2:"

  # Scenario: drag first after second
    When he drags list item in row 1 below item in row 2
    Then he checks list item in row 1 has value "drag3:"
    And he checks list item in row 2 has value "drag2:"

  # Scenario: drag third before first
    When he drags list item in row 3 above item in row 1
    Then he checks list item in row 1 has value "drag4:"
    And he checks list item in row 2 has value "drag3:"

  # Scenario: drag last before first
    When he drags list item in row 4 above item in row 1
    Then he checks list item in row 1 has value "drag1:"
    And he checks list item in row 2 has value "drag4:"

  # Scenario: try drag list item via span" and ensure order hasn't changed
    When he tries to drag list item in row 4 above item in row 1
    Then he checks list item in row 1 has value "drag1:"
    And he checks list item in row 2 has value "drag4:"
    And he checks list item in row 3 has value "drag3:"
    And he checks list item in row 4 has value "drag2:"


  Scenario: Test complex list item single field modifications
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    And he tests list ".tigerProxyCfg.proxyCfg.proxyRoutes"
    And he checks list add button is enabled

    When he adds complex list item
    """
      id: entry1
      from: from1
      to: to1
      basicAuth.username: user1
      basicAuth.password: pwd1
    """
    Then he checks list item in row 1 has value "entry1: [user1@pwd1] from1 ↦ to1"
    When he sets active complex list item to
    """
      id: entry2
    """
    Then he checks list item in row 1 has value "entry2: [user1@pwd1] from1 ↦ to1"
    When he sets active complex list item to
    """
      from: from2
    """
    Then he checks list item in row 1 has value "entry2: [user1@pwd1] from2 ↦ to1"
    When he sets active complex list item to
    """
      to: to2
    """
    Then he checks list item in row 1 has value "entry2: [user1@pwd1] from2 ↦ to2"
    When he sets active complex list item to
    """
      basicAuth.username: user2
    """
    Then he checks list item in row 1 has value "entry2: [user2@pwd1] from2 ↦ to2"
    When he sets active complex list item to
    """
      basicAuth.password: pwd2
    """
    Then he checks list item in row 1 has value "entry2: [user2@pwd2] from2 ↦ to2"
