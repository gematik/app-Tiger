# TODO works with selenium4, serenity 3.1 only
@UniqueBrowser
Feature: Test functionality of simple lists

  Scenario: Adding tigerproxy node
    Given Gerriet is on the homepage
    And he adds a "tigerProxy" node via welcome screen
    And he focuses on formular "tigerProxy_001"
    And he shows "External" tab
    And he unfolds section ".externalJarOptions.options"
    And he tests simple list ".externalJarOptions.options" in formular "tigerProxy_001"

  #Scenario: Adding and inserting an entry to list
    When he adds entry "entry1"
    Then he checks entry with index 1 has value "entry1"

  # Scenario: Inserting an entry to list before active item
    When he selects entry with index 1
    And he adds entry "entry2"
    Then he checks entry with index 1 has value "entry2"
    And he checks entry with index 2 has value "entry1"

  # Scenario: Editing an existing entry
    Given he selects entry with index 2
    When he sets active entry to "entry3"
    Then he checks entry with index 2 has value "entry3"

  # Scenario: Abort editing an entry via ESC
    Given he selects entry with index 1
    When he enters "entry4" to active item
    And he presses ESC
    Then he checks entry with index 1 has value "entry2"

  # Scenario: Abort editing an entry via add button
    When he selects entry with index 1
    And he enters "entry4" to active item
    And he adds entry "entry5"
    And he closes open snack
    Then he checks entry with index 1 has value "entry5"
    And he checks entry with index 2 has value "entry2"

  # TODO add snack detection?

  # Scenario: Abort editing an entry via select other item
    When he selects entry with index 2
    And he enters "entry6" to active item
    And he selects entry with index 1
    And he closes open snack
    Then he checks entry with index 2 has value "entry2"

  # Scenario: Abort editing an entry via delete button
    When he selects entry with index 1
    And he enters "entry6" to active item
    And he deletes active item
    Then he checks entry with index 1 has value "entry2"

  Scenario: Deleting an existing entry
    # check length
    # delete entry
    # check prev sibling is now active
    # check length--

  Scenario: Deleting last entry
    # check length
    # delete entry
    # check prev sibling is now active
    # check length--

  Scenario: Delete is disabled if item selected
    # delete last entry
    # check delete btn disabled

  Scenario: Drag item ff.

