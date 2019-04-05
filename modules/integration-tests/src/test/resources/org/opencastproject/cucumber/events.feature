Feature: Events checks

  Background:
    Given I am on the login page
    And I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    And I select the admin language dropdown
    And I set the current translation to "English"

  Scenario: Minimum viable event
    Given I open the new event modal
    When I set the title to "Test title"
    Then the Next button is enabled
    And I click Next
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/nonsegment-audio.mpg" as presenter video
    Then the Next button is enabled
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/segment.mpg" as slide video
    Then I click Next
    Then I select "Process upon upload and schedule" in the workflow dropdown

  Scenario: I create an event
    Given I open the new event modal
    When I set the title to "Test title"
    And I set the subject to "Test subject"
    And I set the description to "Test description"
    And I set the language dropdown to "English"
    And I set the rights to "Test rights"
    And I set the license to "All rights reserved"
    #And I set the presenter to "Test presenter"
    #And I set the contributor to "Test contributor"
    And I click Next
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/nonsegment-audio.mpg" as presenter video
    Then the Next button is enabled
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/segment.mpg" as slide video
    Then I set the start time to 1970-01-01T00:00:00Z
    Then I click Next