@FullTests @UiTests
Feature: smoke test scenarios

  Background:
    Given I open the main page
    Then I am on the main page

  Scenario: Delete existing route
    When I click on the Routes button
    And I remove the existing route there
    Then The route from "http://tiger.proxy" to "http://localhost:${serverport}" is deleted

  Scenario: Add a route
    Given The request to "http://never-ever-exists.XXX/test1.html" fail

    When I click on the Routes button
    And I add a new route from "http://never-ever-exists.XXX" to "http://127.0.0.1:${serverport}"
    And I wait 2 seconds
    Then I see the new route from "http://never-ever-exists.XXX" to "http://127.0.0.1:${serverport}"
    And I close the route dialog

  Scenario: Check added route is working
    Given I click on the Routes button
    Then I see the new route from "http://never-ever-exists.XXX" to "http://127.0.0.1:${serverport}"
    And I close the route dialog

    When I send successful request to "http://never-ever-exists.XXX/test1.html"
    And I send successful request to "http://never-ever-exists.XXX/test2.html"
    And I switch to manual update mode
    And I update message list
    And I wait 2 seconds
    Then I see the updated message list

  Scenario: Add an incorrect route
    When I click on the Routes button
    And I add a new route from "http://failure.com" to "http://127.0.0.1:20000"
    And I close the route dialog
    And I wait 2 seconds
    Then The request to "http://failure.com/test3.html" fail

    When I switch to manual update mode
    And I update message list
    And I wait 2 seconds
    Then I don't see a message entry for this route

  Scenario: Save message list
    Given I purge the download folder of previous reports

    When I switch to manual update mode
    And I update message list
    And I wait 2 seconds
    Then I see the updated message list

    When I click on the Save button
    Then The entries in message list are downloaded

    When I open the report
    Then I see the contents of the updated message list

  Scenario: Reset message list
    When I switch to manual update mode
    And I update message list
    And I click on the Reset button
    Then Report is empty

  Scenario: Quit Proxy via UI
    Given I switch to test quit mode on UI

    When I quit the tiger proxy via UI
    And I wait 5 seconds
    Then The request to "http://127.0.0.1:${serverport}/webui" times out
    And The request to "http://never-ever-exists.XXX/test1.html" times out







