Feature: Navigation checks

  Background:
    Given I am on the login page
    And I log in as "admin" with "opencast"
    And I am logged in as "Opencast Project Administrator"
    And I open the hamburger

  Scenario: I navigate to the Recordings > Events pane
    When I click the "Recordings" section
    And I open the "Events" pane
    Then I see 0 results

  Scenario: I navigate to the Recordings > Series pane
    When I click the "Recordings" section
    And I open the "Series" pane
    Then I see 0 result

  Scenario: I navigate to the Recordings > Locations pane
    When I click the "Capture" section
    And I open the "Location" pane
    Then I see 0 result

  Scenario: I navigate to the Systems > Jobs pane
    When I click the "Systems" section
    And I open the "Jobs" pane
    Then I see 0 results

  Scenario: I navigate to the Systems > Servers pane
    When I click the "Systems" section
    And I open the "Servers" pane
    Then I see 1 results

  Scenario: I navigate to the Systems > Services pane
    When I click the "Systems" section
    And I open the "Services" pane
    Then I see 73 results
    #Note: This is true of my machine, right now, but likely needs to be a range

  Scenario: I navigate to the Organization > Users pane
    When I click the "Organization" section
    And I open the "Users" pane
    Then I see 2 results

  Scenario: I navigate to the Organization > Groups pane
    When I click the "Organization" section
    And I open the "Groups" pane
    Then I see 2 results

  Scenario: I navigate to the Organization > Access policies pane
    When I click the "Organization" section
    And I open the "Access policies" pane
    Then I see 3 results

  Scenario: I navigate to the Configuration > Themes pane
    When I click the "Configuration" section
    And I open the "Themes" pane
    Then I see 0 results