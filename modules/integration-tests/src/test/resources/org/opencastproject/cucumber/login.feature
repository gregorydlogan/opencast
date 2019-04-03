Feature: Login checks

  Background:
    Given I am on the login page

  Scenario: Logging in as admin
    Given I log in as "admin" with "opencast"
    Then I am logged in as "Opencast Project Administrator"

  Scenario: Logging out
    Given I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    When I log out
    Then I am logged out

  Scenario: Fail Login
    Given I log in as "admin" with "not the right password"
    Then I fail login
