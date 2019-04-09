package org.opencastproject.cucumber;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.java.en.Then;

public class NavigationSteps {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  @Then("I open the hamburger")
  public void openMenu() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"nav-container\"]"));
    boolean initialVisibility = element.isDisplayed();
    if (initialVisibility) {
      fail("Hamburger is already open, check the state for this test");
    }
    element = driver.findElement(By.xpath("//*[@id=\"menu-toggle\"]"));
    element.click();
    assertTrue("Menu visibility should have changed states", element.isDisplayed() != initialVisibility);
  }

  @Then("I click the {string} section")
  public void selectSection(String section) {
    WebElement element = driver.findElement(By.xpath("//*[@title=\"" + section + "\"]"));
    try {
      new WebDriverWait(driver, 2).until(ExpectedConditions.visibilityOf(element));
    } catch (TimeoutException e) {
      fail("Sections are not visible");
    }
    element.click();
    try {
      new WebDriverWait(driver, 2).until(ExpectedConditions.invisibilityOf(element));
    } catch (TimeoutException e) {
      fail("Sections are still visible");
    }
    //TODO: How do we check that this actually got us where we want to be?
    //Hack: Use the I open the X pane immediately after, but that sucks
  }

  @Then("I open the {string} pane")
  public void selectPane(String pane) {
    WebElement element = driver.findElement(By.linkText(pane));
    element.click();
    try {
      new WebDriverWait(driver, 2)
              .until(ExpectedConditions.attributeContains(By.linkText(pane), "class", "active"));
    } catch (TimeoutException e) {
      fail(pane + " did not appear in the page");
    }
  }
}
