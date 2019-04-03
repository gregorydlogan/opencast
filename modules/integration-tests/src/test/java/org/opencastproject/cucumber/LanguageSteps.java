package org.opencastproject.cucumber;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.java.After;
import cucumber.api.java.en.Then;

public class LanguageSteps {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  @Then("I select the admin language dropdown")
  public void selectLanguageDrop() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"lang-dd\"]"));
    element.click();
    element = driver.findElement(By.xpath("//*[@id=\"lang-dd\"]/ul"));
    assertTrue(element.isDisplayed());
  }

  @Then("I select {string} and Events reads {string}")
  public void selectLanguage(String language, String eventsText) {
    WebElement element = driver.findElement(By.linkText(language));
    element.click();
    new WebDriverWait(driver,2L).until(new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        WebElement element = d.findElement(By.cssSelector("#lang-dd > ul"));
        assertFalse(element.isDisplayed());
        element = driver.findElement(By.xpath("/html/body/section/div/div[1]/h1"));
        return element.getText().equalsIgnoreCase(eventsText);
      }
    });
  }

  @Then("I select {string} and the welcome page reads {string}")
  public void selectWelcomeLanguage(String language, String eventsText) {
    WebElement element = driver.findElement(By.linkText(language));
    element.click();
    new WebDriverWait(driver,2L).until(new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        WebElement element = d.findElement(By.cssSelector("#lang-dd > ul"));
        assertFalse(element.isDisplayed());
        element = driver.findElement(By.xpath("/html/body/section/div/div/form/div[1]/p/span"));
        return element.getText().equalsIgnoreCase(eventsText);
      }
    });
  }
}