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

  @Then("^I should be \"(.*)\"$")
  public void checkUsername(String username) {
    new WebDriverWait(driver,5L).until(new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        return driver.findElement(By.xpath("//*[@id=\"user-dd\"]/div")).getText()
                .equals(username);
      }
    });
  }

  @Then("^I log out$")
  public void logout() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"user-dd\"]"));
    element.click();
    element = driver.findElement(By.cssSelector("span.logout-icon"));
    element.click();
  }

  @Then("^I am logged out$")
  public void amLoggedOut() {
    WebElement element = driver.findElement(By.xpath("/html/body/section/div/div/form/div[1]/p/span"));
    element.getText().equalsIgnoreCase("Welcome to Opencast");
  }

  @After()
  public void closeBrowser() {
    driver.quit();
  }
}