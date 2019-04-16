Feature: Filter checks

  Background:
    Given I am on the login page
    And I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    And I select the admin language dropdown
    And I set the current translation to "English"

  @focus
  Scenario: Event type filtering
    Given I click on the "Scheduled" filter
    Then I see 1 filter active
    And I see the "Status":"Scheduled" filter active
    Then I click on the "Recording" filter
    And I see 1 filter active
    And I see the "Status":"Recording" filter active
    Then I click on the "Recording" filter
    And I see 1 filter active
    And I see the "Status":"Recording" filter active