Feature: Login checks

  Scenario: Logging in as admin
    Given I am on the login page
    When I log in as "admin" with "opencast"
    Then I am logged in as "Opencast Project Administrator"

  Scenario: Logging out
    Given I am on the login page
    And I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    When I log out
    Then I am logged out

  Scenario: Fail Login
    Given I am on the login page
    When I log in as "admin" with "not the right password"
    Then I fail login

  Scenario: I change languages while logged in
    Given I am on the login page
    And I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    When I select the admin language dropdown
    Then I select "Deutsch" and Events reads "Ereignisse"
    Then I select the admin language dropdown
    Then I select "English" and Events reads "Events"

  Scenario: I change languages while logged out
    Given I am on the login page
    When I select the admin language dropdown
    Then I select "Deutsch" and the welcome page reads "Willkommen bei Opencast"
    Then I select the admin language dropdown
    Then I select "English" and the welcome page reads "Welcome to Opencast"
