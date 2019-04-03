package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class LoginSteps {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  @Given("I am on the login page")
  public void goLogin() {
    driver.get("http://localhost:8080");
  }

  @When("I log in as {string} with {string}")
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

  @Then("I am logged in as {string}")
  public void checkUsername(String username) {
    try {
      new WebDriverWait(driver, 2).until(ExpectedConditions
              .textToBePresentInElementLocated(By.xpath("//*[@id=\"user-dd\"]/div"), username));
    } catch (TimeoutException e) {
      fail(username + " did not appear where the logged in username appears");
    }
  }

  @Then("I log out")
  public void logout() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"user-dd\"]"));
    element.click();
    element = driver.findElement(By.cssSelector("span.logout-icon"));
    element.click();
  }

  @Then("I fail login")
  public void failLogin() {
    WebElement element = (new WebDriverWait(driver, 2))
            .until(ExpectedConditions.visibilityOf(driver.findElement(By.xpath("/html/body/section/div/div/form/div[2]"))));
    assertTrue("Login failure message missing",
            element.getText().equalsIgnoreCase("Incorrect username and / or password"));
  }

  @Then("I am logged out")
  public void amLoggedOut() {
    WebElement element = driver.findElement(By.xpath("/html/body/section/div/div/form/div[1]/p/span"));
    assertTrue(element.getText().equalsIgnoreCase("Welcome to Opencast"));
  }
}