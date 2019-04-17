package org.opencastproject.cucumber;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
        plugin = {"pretty"},
        //features = {
        //        "src/test/resources/org/opencastproject/cucumber/series.feature",
        //        "src/test/resources/org/opencastproject/cucumber/events.feature"
        //},
        tags = "@focus")
public class RunCucumberTest {
}
