@SingleBrowser
@FullTests @UnitTest
Feature: Test functionality of simple lists

  Scenario: Test simple list item management
    Given Gerriet is on the homepage
    And he adds a "externalJar" node via welcome screen
    And he focuses on formular "externalJar_001"
    And he shows "External" tab
    And he unfolds section ".externalJarOptions.options"
    And he tests list ".externalJarOptions.options"
    And he checks that add button for simple list is enabled

  #Scenario: Adding and inserting an entry to list
    When he adds list item "entry1"
    Then he checks list item in row 1 has value "entry1"

  # Scenario: Inserting an entry to list before active item
    When he selects list item in row 1
    And he adds list item "entry2"
    Then he checks list item in row 1 has value "entry1"
    And he checks list item in row 2 has value "entry2"

  # Scenario: Editing an existing entry
    Given he selects list item in row 2
    When he sets active list item to "entry3"
    Then he checks list item in row 2 has value "entry3"

  # Scenario: Abort editing an entry via ESC
    Given he selects list item in row 1
    When he enters "entry4" to active list item
    And he presses ESC on editing list item
    Then he checks list item in row 1 has value "entry1"

  # Scenario: Auto apply an entry via add button
    When he selects list item in row 1
    And he enters "entry4" to active list item
    And he adds list item "entry5"
    # And he closes open snack
    Then he checks list item in row 1 has value "entry4"
    And he checks list item in row 2 has value "entry5"

  # TODO add snack detection?

  # Scenario: Auto apply an entry via select other item
    When he selects list item in row 2
    And he enters "entry6" to active list item
    And he selects list item in row 1
    # And he closes open snack
    Then he checks list item in row 2 has value "entry6"

  # Scenario: Abort editing an entry via delete button
    When he selects list item in row 1
    And he enters "entry7" to active list item
    And he deletes active list item
    Then he checks list item in row 1 has value "entry6"

  # Scenario: Deleting an existing entry
    Given he checks list length is 2
    And he selects list item in row 1
    When he deletes active list item
    And he checks active list item is in row 1
    And he checks list item in row 1 has value "entry3"
    And he checks list length is 1

  # Scenario: check if i delete last entry in list then previous prelast is active
    Given he adds list item "entry9"
    And he adds list item "entry8"
    And he selects list item in row 3
    When he deletes active list item
    Then he checks active list item is in row 2

  # Scenario: Deleting last remaining entries
    When he deletes active list item
    And he deletes active list item
    Then he checks list length is 0


  Scenario: Drag items in simple list
    Given Gerriet is on the homepage
    And he adds a "externalJar" node via welcome screen
    And he focuses on formular "externalJar_001"
    And he shows "External" tab
    And he unfolds section ".externalJarOptions.options"
    And he tests list ".externalJarOptions.options"
    And he adds list item "drag1"
    And he adds list item "drag2"
    And he adds list item "drag3"
    And he adds list item "drag4"

  # Scenario: drag first after last pos
    Given he scrolls to row 4 in list
    When he drags list item in row 1 below item in row 4
    Then he checks list item in row 4 has value "drag1"
    And he checks list item in row 1 has value "drag2"

  # Scenario: drag first after second
    When he drags list item in row 1 below item in row 2
    Then he checks list item in row 1 has value "drag3"
    And he checks list item in row 2 has value "drag2"

  # Scenario: drag third before first
    When he drags list item in row 3 above item in row 1
    Then he checks list item in row 1 has value "drag4"
    And he checks list item in row 2 has value "drag3"

  # Scenario: drag last before first
    When he drags list item in row 4 above item in row 1
    Then he checks list item in row 1 has value "drag1"
    And he checks list item in row 2 has value "drag4"

  # Scenario: try drag list item via span" and ensure order hasn't changed
    When he tries to drag list item in row 4 above item in row 1
    Then he checks list item in row 1 has value "drag1"
    And he checks list item in row 2 has value "drag4"
    And he checks list item in row 3 has value "drag3"
    And he checks list item in row 4 has value "drag2"

