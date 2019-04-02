package org.opencastproject.cucumber;

import cucumber.api.java.After;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class Stepdefs {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  @Given("^I am on the login page$")
  public void goLogin() {
    driver.get("https:\\stable.opencast.org");
  }

  @When("^I log in as \"(.*)\" with \"(.*)\"$")
  public void doLogin(String user, String pass) {
    WebElement element = driver.findElement(By.name("j_username"));
    // Enter something to search for
    element.clear();
    element.sendKeys(user);
    element = driver.findElement(By.name("j_password"));
    element.clear();
    element.sendKeys(pass);
    // Now submit the form. WebDriver will find the form for us from the element
    element.submit();
  }

  @Then("^the header should be \"(.*)\"$")
  public void checkHeader(String header) {
    // Our pages are rendered dynamically
    // Wait for the page to load timeout after ten seconds
    new WebDriverWait(driver,2L).until(new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        return d.getTitle().toLowerCase().startsWith(header);
      }
    });
  }

  @After()
  public void closeBrowser() {
    driver.quit();
  }
}