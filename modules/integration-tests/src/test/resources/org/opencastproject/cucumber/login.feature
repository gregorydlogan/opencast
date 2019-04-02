Feature: Login checks

  Scenario: Logging in and back out as admin
    Given I am on the login page
    When I log in as "admin" with "opencast"
    Then I should be "Opencast Project Administrator"
    Then I log out
    Then I am logged out

  Scenario: Fail Login
    Given I am on the login page
    When I log in as "admin" with "not the right password"
    Then I fail login

  Scenario: I change languages
    Given I am on the login page
    When I log in as "admin" with "opencast"
    Then I should be "Opencast Project Administrator"
    Then I select the admin language dropdown
    Then I select "Deutsch" and Events reads "Ereignisse"
    Then I select the admin language dropdown
    Then I select "English" and Events reads "Events"
