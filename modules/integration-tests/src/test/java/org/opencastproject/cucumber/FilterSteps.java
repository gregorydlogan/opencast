package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;
import static org.opencastproject.cucumber.WebDriverFactory.getDriver;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class FilterSteps {

  @When("I click on the {string} filter")
  public void setFilter(String name) {
    WebElement element = getDriver().findElement(By.xpath("//*[@title=\"" + name + "\"]"));
    element.click();
  }

  @When("I search for {string}")
  public void setSearch(String search) {
    WebElement element = getDriver().findElement(By.xpath("/html/body/section/div/div[1]/div/div[2]/input"));
    element.click();
    element.sendKeys(search);
  }

  @When("I clear all filters")
  public void clearFilters() {
    WebElement element = getDriver().findElement(By.xpath("/html/body/section/div/div[1]/div/div[2]/div/i[1]"));
    element.click();
  }

  @Then("I see {int} filter(s) active")
  public void seeFilters(int count) {
    List<WebElement> filters = getDriver().findElements(By.xpath("/html/body/section/div/div[1]/div/div[2]/div/div[1]/span[@ng-if=\"filter.value\"]"));
    assertTrue("Incorrect number of active filters, is currently " + filters.size(), filters.size() == count);
  }

  @Then("I see the {string}:{string} filter active")
  public void seeFilter(String filter, String value) {
    WebElement element = getDriver().findElement(
            By.xpath("/html/body/section/div/div[1]/div/div[2]/div/div[1]/span[1]/span/span[contains(text(), \"" + filter + "\")]/span[contains(text(), \"" + value + "\")]"));

  }
}
