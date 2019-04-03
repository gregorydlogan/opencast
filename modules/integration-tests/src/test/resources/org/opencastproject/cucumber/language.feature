Feature: Language checks

  Background:
    Given I am on the login page

  Scenario: I change languages while logged in
    Given I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    When I select the admin language dropdown
    Then I select "Deutsch" and Events reads "Ereignisse"
    Then I select the admin language dropdown
    Then I select "English" and Events reads "Events"

  Scenario: I change languages while logged out
    Given I select the admin language dropdown
    When I select "Deutsch" and the welcome page reads "Willkommen bei Opencast"
    Then I select the admin language dropdown
    Then I select "English" and the welcome page reads "Welcome to Opencast"
