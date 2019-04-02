Feature: Login checks

  Scenario: Logging in and back out
    Given I am on the login page
    When I log in as "admin" with "opencast"
    Then I should be "Opencast Project Administrator"
    Then I log out
    Then I am logged out