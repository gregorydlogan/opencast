package org.opencastproject.cucumber;

import cucumber.api.java.After;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class Stepdefs {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  @Given("^I am on the Google search page$")
  public void I_visit_google() {
    driver.get("https:\\www.google.com");
  }

  @When("^I search for \"(.*)\"$")
  public void search_for(String query) {
    WebElement element = driver.findElement(By.name("q"));
    // Enter something to search for
    element.sendKeys(query);
    // Now submit the form. WebDriver will find the form for us from the element
    element.submit();
  }

  @Then("^the page title should start with \"(.*)\"$")
  public void checkTitle(String titleStartsWith) {
    // Google's search is rendered dynamically with JavaScript
    // Wait for the page to load timeout after ten seconds
    new WebDriverWait(driver,10L).until(new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        return d.getTitle().toLowerCase().startsWith(titleStartsWith);
      }
    });
  }

  @After()
  public void closeBrowser() {
    driver.quit();
  }
}