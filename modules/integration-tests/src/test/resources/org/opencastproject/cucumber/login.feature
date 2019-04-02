Feature: Login checks

  Scenario: Logging in
    Given I am on the login page
    When I log in as "admin" with "opencast"
    Then the header should be "opencast"