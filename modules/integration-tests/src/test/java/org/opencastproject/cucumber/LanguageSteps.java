package org.opencastproject.cucumber;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class LanguageSteps {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  private void isDropdownHidden(By locator) {
    try {
      new WebDriverWait(driver, 2)
              .until(ExpectedConditions.invisibilityOfElementLocated(locator));
    } catch (TimeoutException e) {
      fail("The language selection dropdown has not disappeared");
    }
  }

  private void isHeaderTranslated(By locator, String expected) {
    try {
      new WebDriverWait(driver, 2)
              .until(ExpectedConditions.textToBePresentInElementLocated(locator, expected));
    } catch (TimeoutException e) {
      fail("The header did not translate");
    }
  }

  @Then("I select the admin language dropdown")
  public void selectLanguageDrop() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"lang-dd\"]"));
    element.click();
    element = driver.findElement(By.xpath("//*[@id=\"lang-dd\"]/ul"));
    assertTrue(element.isDisplayed());
  }

  @When("I set the current translation to {string}")
  public void setLanguage(String language) {
    //Select the language
    WebElement element = driver.findElement(By.linkText(language));
    element.click();
  }

  @Then("I select {string} and Events reads {string}")
  public void selectLanguage(String language, String eventsText) {
    setLanguage(language);

    isDropdownHidden(By.cssSelector("#lang-dd > ul"));
    isHeaderTranslated(By.xpath("/html/body/section/div/div[1]/h1"), eventsText);
  }

  @Then("I select {string} and the welcome page reads {string}")
  public void selectWelcomeLanguage(String language, String welcomeText) {
    setLanguage(language);

    isDropdownHidden(By.cssSelector("#lang-dd > ul"));
    isHeaderTranslated(By.xpath("/html/body/section/div/div/form/div[1]/p/span"), welcomeText);
  }
}