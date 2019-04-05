package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.PendingException;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class EventsSteps {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  private void setTextRowContent(int row, String content) {
    setTextRowContent(row, content, "input");
  }

  private void setTextRowContent(int row, String content, String inputType) {
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

  //Note: This only works with Chosen!
  private void setDropdownContent(int row, String content) {
    String rawXPath = "//*[@id=\"add-event-modal\"]/admin-ng-wizard/ng-include/div[1]/div/div/div/div/form/table/tbody/tr[" + row + "]/td[2]/div";
    String inputXPath = rawXPath + "/div/div/div/ul/li[text()=\"" + content + "\"]";
    String renderedXPath = rawXPath + "/span";

    //Check that the row is empty
    new WebDriverWait(driver, 2)
            .until(ExpectedConditions.textToBe(By.xpath(renderedXPath), "No option selected"));
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
    assertTrue("Input text matches content in the span", element.getText().equals(content));
  }

  @Then("the Next button is enabled")
  public WebElement nextEnabled() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"add-event-modal\"]/admin-ng-wizard/footer/a"));
    assertTrue("Next button is not enabled!", element.isEnabled());
    return element;
}

  @Then("I click Next")
  public void clickNext() {
    WebElement element = nextEnabled();
    element.click();
  }

  @When("I open the new event modal")
  public void newEvent() {
    WebElement element = (new WebDriverWait(driver, 2)
            .until(ExpectedConditions.elementToBeClickable(By.xpath("/html/body/section/section/ng-include/div/button/span"))));
    element.click();
  }

  @Then("I set the title to {string}")
  public void setTitle(String title) {
    setTextRowContent(1, title);
  }

  @Then("I set the subject to {string}")
  public void setSubject(String series) {
    setTextRowContent(2, series);
  }

  @Then("I set the description to {string}")
  public void setDescription(String description) {
    setTextRowContent(3, description, "textarea");
  }

  @Then("I set the language dropdown to {string}")
  public void setLanguage(String language) {
    setDropdownContent(4, language);
  }

  @Then("I set the rights to {string}")
  public void setRIghts(String rights) {
    setTextRowContent(5, rights);
  }

  @Then("I set the license to {string}")
  public void setLicense(String license) {
    setDropdownContent(6, license);
  }

  @Then("I set the series to {string}")
  public void setSeries(String series) {
    setDropdownContent(7, series);
  }

  @Then("I set the presenter to {string}")
  public void setPresenter(String presenter) {
    throw new PendingException("This UI element isn't quite a normal dropdown");
    //setTextRowContent(8, presenter);
  }

  @Then("I set the presenters to {string}, {string}, and {string}")
  public void setPresenters(String a, String b, String c) {
    throw new PendingException("Need to implement this still");
  }

  @Then("I set the contributor to {string}")
  public void setContributor(String contributor) {
    throw new PendingException("This UI element isn't quite a normal dropdown");
    //setTextRowContent(8, contributor);
  }

  @Then("I set the contributors to {string}, {string}, and {string}")
  public void setContributors(String a, String b, String c) {
    throw new PendingException("Need to implement this still");
  }
}
