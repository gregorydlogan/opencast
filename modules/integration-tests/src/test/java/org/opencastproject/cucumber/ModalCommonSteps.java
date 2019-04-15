package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class ModalCommonSteps {

  private static final WebDriver driver = WebDriverFactory.createWebDriver();

  public static void setTextRowContent(String type, int row, String content) {
    setTextRowContent(type, row, content, "input");
  }

  public static void setTextRowContent(String type, int row, String content, String inputType) {
    String rawXPath = "//*[@id=\"add-" + type + "-modal\"]/admin-ng-wizard/ng-include/div[1]/div/div/div/div/form/table/tbody/tr[" + row + "]/td[2]/div";
    String inputXPath = rawXPath + "/" + inputType;
    String renderedXPath = rawXPath + "/span";

    //Check that the row is empty
    new WebDriverWait(driver, 2)
            .until(ExpectedConditions.textToBe(By.xpath(renderedXPath), ""));
    //Click into the row
    WebElement element = (new WebDriverWait(driver, 2)
            .until(ExpectedConditions.elementToBeClickable(
                    By.xpath(rawXPath))));
    element.click();
    //A temporary input element has appeared, find it
    element = new WebDriverWait(driver, 2)
            .until(ExpectedConditions.elementToBeClickable(
                    By.xpath(inputXPath)));
    element.sendKeys(content);
    //Get out of the input box (this xpath is the parent of the input)
    element = driver.findElement(By.xpath(rawXPath + "/../../.."));
    element.click();
    //Check that the rendered version matches
    element = driver.findElement(By.xpath(renderedXPath));
    assertTrue("Input text doesn't match the content in the span", element.getText().equals(content));
  }

  public static void setDropdownContent(String rawXPath, String inputXPath, String renderedXPath, String emptyText) {
    //Check that the row is empty
    new WebDriverWait(driver, 2)
            .until(ExpectedConditions.textToBe(By.xpath(renderedXPath), emptyText));
    //Click into the row
    WebElement element = (new WebDriverWait(driver, 2)
            .until(ExpectedConditions.elementToBeClickable(
                    By.xpath(rawXPath))));
    element.click();
    //A temporary set of input elements have appeared, find the right one
    element = new WebDriverWait(driver, 2)
            .until(ExpectedConditions.elementToBeClickable(
                    By.xpath(inputXPath)));
    element.click();
    //Get out of the input box
    element = driver.findElement(By.xpath(rawXPath + "/../../.."));
    element.click();
  }

  public static void setDropdownContentAndVerify(String rawXPath, String inputXPath, String renderedXPath, String emptyText, String expected) {
    setDropdownContent(rawXPath, inputXPath, renderedXPath, emptyText);
    //Check that the rendered version matches
    WebElement element = driver.findElement(By.xpath(renderedXPath));
    assertTrue("Input text doesn't match the content in the span", element.getText().equals(expected));
  }

  //Note: This only works with Chosen!
  public static void setMetadataDropdownContent(String type, int row, String content) {
    String rawXPath = "//*[@id=\"add-" + type + "-modal\"]/admin-ng-wizard/ng-include/div[1]/div/div/div/div/form/table/tbody/tr[" + row + "]/td[2]/div";
    String inputXPath = rawXPath + "/div/div/div/ul/li[text()=\"" + content + "\"]";
    String renderedXPath = rawXPath + "/span";
    setDropdownContentAndVerify(rawXPath, inputXPath, renderedXPath, "No option selected", content);
  }

  //Note: This only works with Chosen!
  public static void setAclDropdownContent(String rawXPath, String acl) {
    String inputXPath = rawXPath + "/div/ul/li[text()=\"" + acl + "\"]";
    String renderedXPath = rawXPath + "/a/span";
    //Note: We're not verifying here because this dropdown renders the empty text once selected
    //This makes sense when you think about the UI: You select the template, it fills it out, but you're *not* editing the template
    //Thus, the dropdown should be back to its initial state
    setDropdownContent(rawXPath, inputXPath, renderedXPath, "Select a template");
  }

  @Then("the Next button is enabled in the {string} modal")
  public static WebElement nextEnabled(String type) {
    WebElement element = WebDriverFactory.createWebDriver().findElement(By.xpath("//*[@id=\"add-" + type+ "-modal\"]/admin-ng-wizard/footer/a"));
    assertTrue("Next/Create button is not enabled!", element.isEnabled());
    return element;
  }

  @Then("I click Next/Create in the {string} modal")
  public static void clickNext(String type) {
    WebElement element = nextEnabled(type);
    element.click();
  }

  @When("I open the new event/series modal")
  public static void newEvent() {
    WebElement element = (new WebDriverWait(WebDriverFactory.createWebDriver(), 2)
            .until(ExpectedConditions.elementToBeClickable(By.xpath("/html/body/section/section/ng-include/div/button"))));
    element.click();
  }

}
