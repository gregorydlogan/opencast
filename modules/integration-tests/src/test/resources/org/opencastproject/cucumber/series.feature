Feature: Series checks

  Background:
    Given I am on the login page
    And I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    And I select the admin language dropdown
    And I set the current translation to "English"
    And I open the hamburger
    And I click the "Recordings" section
    And I open the "Series" pane

  Scenario: Minimum viable series
    Given I record the number of results
    And I open the new series modal
    When I set the title to "Test title" in the series modal
    Then the Next button is enabled in the "series" modal
    And I click Next in the "series" modal
    Then I select "public" in the new series acl dropdown
    Then I click Next in the "series" modal
    Then I click Next in the "series" modal
    Then I verify my selections on the series confirmation modal
    Then I click Create in the "series" modal
    Then I check that the number of results has increased by 1

  Scenario: I create a series
    Given I record the number of results
    And I open the new series modal
    When I set the title to "Test title" in the series modal
    And I set the subject to "Test subject" in the series modal
    And I set the description to "Test description" in the series modal
    And I set the language dropdown to "English" in the series modal
    And I set the rights to "Test rights" in the series modal
    And I set the license to "All rights reserved" in the series modal
    And I set the presenter to "Test presenter" in the series modal
    And I set the contributor to "Test contributor" in the series modal
    And I set the publisher to "Test publisher" in the series modal
    And I click Next in the "series" modal
    Then I select "public" in the new series acl dropdown
    Then I click Next in the "series" modal
    Then I select the "Test" theme in the series modal
    Then I click Next in the "series" modal
    Then I verify my selections on the series confirmation modal
    Then I click Create in the "series" modal
    Then I check that the number of results has increased by 1
