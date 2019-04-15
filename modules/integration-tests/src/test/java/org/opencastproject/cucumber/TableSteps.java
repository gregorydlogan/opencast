package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opencastproject.cucumber.WebDriverFactory.getDriver;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

import cucumber.api.java.en.Then;

public class TableSteps {

  private static final By rowCounter = By.xpath("/html/body/section/div/div[1]/h4");
  private static final By singleRow = By.xpath("/html/body/section/div/div[2]/table/tbody/tr/td");
  private static final By tableRows = By.xpath("/html/body/section/div/div[2]/table/tbody/tr");

  private int expected = 0;

  private int checkCounterAndRowConsistency() {
    //Wait to make sure things have rendered
    new WebDriverWait(getDriver(), 1);
    WebElement counter = getDriver().findElement(rowCounter);
    List<WebElement> rows = getDriver().findElements(tableRows);
    //Remove the " rows" from "N rows"
    int count = Integer.parseInt(counter.getText().replace(" rows", ""));
    if (0 != count) {
      if (count <= 10) {
        assertTrue("Row count and number of rows don't match, found " + rows.size() + " should be " + count,
                rows.size() == count);
      }
      else {
        //TODO: Handle different size pages
        assertTrue("Row count and number of rows don't match, found " + count + " which is more than 10, but "
                + rows.size() + " are being displayed",
                rows.size() == 10);
      }
    } else {
      assertTrue("Row count and number of rows don't match, found " + rows.size() + " should be 1",
              rows.size() == 1);
    }
    return count;
  }

  @Then("I record the number of results")
  public void storeResults() {
    expected = checkCounterAndRowConsistency();
  }

  @Then("I check that the number of results has increased by {int}")
  public void hasIncreasedBy(int number) {
    new WebDriverWait(getDriver(), 10)
            .until(ExpectedConditions.textToBePresentInElementLocated(rowCounter, (expected + number) + " rows"));
    int current = checkCounterAndRowConsistency();
    assertTrue("Incorrect number of results, should be " + (expected + number) + " but is " + current,
            current == expected + number);
    expected = current;
  }

  @Then("I see {int} result(s)")
  public void checkVisibleResults(int count) {
    try {
      new WebDriverWait(getDriver(), 2)
              .until(ExpectedConditions
                      .textToBePresentInElementLocated(rowCounter, count + " rows"));
    } catch (TimeoutException e) {
      fail("Incorrect number of results in the table summary, should be " + count);
    }
    List<WebElement> elements = getDriver().findElements(tableRows);
    if (count != 0) {
      assertTrue("Incorrect number of results in the table body, should be " + count, elements.size() == count);
    } else {
      assertTrue("Incorrect number of results in the table body, should be 0 and read 'No results found'",
              elements.get(0).getText().equalsIgnoreCase("No results found"));
    }
  }
}
