package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import cucumber.api.java.After;

import org.junit.Assert;
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

  @Then("^I fail login$")
  public void failLogin() {
    WebElement element = driver.findElement(By.xpath("/html/body/section/div/div/form/div[2]"));
    element.isDisplayed();
    assertTrue(element.getText().equalsIgnoreCase("Incorrect username and / or password"));
  }

  @Then("^I am logged out$")
  public void amLoggedOut() {
    WebElement element = driver.findElement(By.xpath("/html/body/section/div/div/form/div[1]/p/span"));
    assertTrue(element.getText().equalsIgnoreCase("Welcome to Opencast"));
  }

  @Then("^I select the admin language dropdown")
  public void selectLanguageDrop() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"lang-dd\"]/div/img"));
    element.click();
    element = driver.findElement(By.xpath("//*[@id=\"lang-dd\"]/ul"));
    assertTrue(element.isDisplayed());
  }

  @Then("^I select \"(.*)\" and Events reads \"(.*)\"$")
  public void selectLanguage(String language, String eventsText) {
    WebElement element = driver.findElement(By.linkText(language));
    element.click();
    new WebDriverWait(driver,2L).until(new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        WebElement element = d.findElement(By.linkText(language));
        assertFalse(element.isDisplayed());
        element = driver.findElement(By.xpath("/html/body/section/div/div[1]/h1"));
        return element.getText().equalsIgnoreCase(eventsText);
      }
    });
  }

  @After()
  public void closeBrowser() {
    driver.quit();
  }
}