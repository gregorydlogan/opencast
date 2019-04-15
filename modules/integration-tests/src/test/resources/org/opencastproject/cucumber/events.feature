Feature: Events checks

  Background:
    Given I am on the login page
    And I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    And I select the admin language dropdown
    And I set the current translation to "English"
    And I open the hamburger
    And I click the "Recordings" section
    And I open the "Events" pane

  Scenario: Minimum viable event
    Given I record the number of results
    And I open the new event modal
    When I set the title to "Test title" in the event modal
    Then the Next button is enabled in the "event" modal
    And I click Next in the "event" modal
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/nonsegment-audio.mpg" as presenter video
    Then the Next button is enabled in the "event" modal
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/segment.mpg" as slide video
    Then I click Next in the "event" modal
    Then I select "Process upon upload and schedule" in the new event workflow dropdown
    Then I set the "flagQuality360p" workflow flag to "true"
    And I set the "flagQuality720p" workflow flag to "false"
    Then I click Next in the "event" modal
    Then I select "public" in the new event acl dropdown
    Then I click Next in the "event" modal
    Then I verify my selections on the event confirmation modal
    Then I click Create in the "event" modal
    Then I check that the number of results has increased by 1
    And I wait until the event has uploaded

  Scenario: I create an event
    Given I record the number of results
    And I open the new event modal
    When I set the title to "Test title" in the event modal
    And I set the subject to "Test subject" in the event modal
    And I set the description to "Test description" in the event modal
    And I set the language dropdown to "English" in the event modal
    And I set the rights to "Test rights" in the event modal
    And I set the license to "All rights reserved" in the event modal
    And I set the presenter to "Test presenter" in the event modal
    And I set the contributor to "Test contributor" in the event modal
    And I click Next in the "event" modal
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/nonsegment-audio.mpg" as presenter video
    Then the Next button is enabled in the "event" modal
    Then I upload "/home/greg/opencast/mediapackages/mh_demo/segment.mpg" as slide video
    Then I set the start time to 1970-01-01T00:00:00Z
    Then I select "Process upon upload and schedule" in the new event workflow dropdown
    Then I set the "flagQuality360p" workflow flag to "true"
    And I set the "flagQuality720p" workflow flag to "false"
    Then I click Next in the "event" modal
    Then I select "public" in the new event acl dropdown
    Then I click Next in the "event" modal
    Then I verify my selections on the event confirmation modal
    Then I click Create in the "event" modal
    Then I check that the number of results has increased by 1
