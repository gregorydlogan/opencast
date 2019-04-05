package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ModalCommonSteps {

  private static final WebDriver driver = WebDriverFactory.createWebDriver();

  public static void setTextRowContent(int row, String content) {
    setTextRowContent(row, content, "input");
  }

  public static void setTextRowContent(int row, String content, String inputType) {
    String rawXPath = "//*[@id=\"add-event-modal\"]/admin-ng-wizard/ng-include/div[1]/div/div/div/div/form/table/tbody/tr[" + row + "]/td[2]/div";
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
    //Get out of the input box (this xpath is the header of the modal)
    element = driver.findElement(By.xpath("//*[@id=\"add-event-modal\"]/header/h2"));
    element.click();
    //Check that the rendered version matches
    element = driver.findElement(By.xpath(renderedXPath));
    assertTrue("Input text matches content in the span", element.getText().equals(content));
  }

  public static void setDropdownContent(String rawXPath, String inputXPath, String renderedXPath, String emptyText, String expected) {
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
    //Get out of the input box (this xpath is the header of the modal
    element = driver.findElement(By.xpath("//*[@id=\"add-event-modal\"]/header/h2"));
    element.click();
    //Check that the rendered version matches
    element = driver.findElement(By.xpath(renderedXPath));
    assertTrue("Input text matches content in the span", element.getText().equals(expected));
  }

  //Note: This only works with Chosen!
  public static void setMetadataDropdownContent(int row, String content) {
    String rawXPath = "//*[@id=\"add-event-modal\"]/admin-ng-wizard/ng-include/div[1]/div/div/div/div/form/table/tbody/tr[" + row + "]/td[2]/div";
    String inputXPath = rawXPath + "/div/div/div/ul/li[text()=\"" + content + "\"]";
    String renderedXPath = rawXPath + "/span";
    setDropdownContent(rawXPath, inputXPath, renderedXPath, "No option selected", content);
  }

  //Note: This only works with Chosen!
  public static void setWorkflowDropdownContent(String workflow) {
    String rawXPath = "//*[@id=\"add-event-modal\"]/admin-ng-wizard/ng-include/div[5]/div/div/div/div/div[1]";
    String inputXPath = rawXPath + "/div/div/div/ul/li[text()=\"" + workflow+ "\"]";
    String renderedXPath = rawXPath + "/a/span";
    setDropdownContent(rawXPath, inputXPath, renderedXPath, "No option selected", workflow);
  }
}
