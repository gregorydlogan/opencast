package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;
import static org.opencastproject.cucumber.ModalCommonSteps.setAclDropdownContent;
import static org.opencastproject.cucumber.ModalCommonSteps.setMetadataDropdownContent;
import static org.opencastproject.cucumber.ModalCommonSteps.setTextRowContent;
import static org.opencastproject.cucumber.ModalCommonSteps.setWorkflowDropdownContent;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import cucumber.api.PendingException;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class EventsSteps {

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  private LinkedHashMap<String, String> settings = new LinkedHashMap<>();

  @Then("the Next button is enabled")
  public WebElement nextEnabled() {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"add-event-modal\"]/admin-ng-wizard/footer/a"));
    assertTrue("Next/Create button is not enabled!", element.isEnabled());
    return element;
}

  @Then("I click Next/Create")
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
    settings.put("Title", title);
  }

  @Then("I set the subject to {string}")
  public void setSubject(String subject) {
    setTextRowContent(2, subject);
    settings.put("Subject", subject);
  }

  @Then("I set the description to {string}")
  public void setDescription(String description) {
    setTextRowContent(3, description, "textarea");
    settings.put("Description", description);
  }

  @Then("I set the language dropdown to {string}")
  public void setLanguage(String language) {
    setMetadataDropdownContent(4, language);
    settings.put("Language", language);
  }

  @Then("I set the rights to {string}")
  public void setRights(String rights) {
    setTextRowContent(5, rights);
    settings.put("Rights", rights);
  }

  @Then("I set the license to {string}")
  public void setLicense(String license) {
    setMetadataDropdownContent(6, license);
    settings.put("License", license);
  }

  @Then("I set the series to {string}")
  public void setSeries(String series) {
    setMetadataDropdownContent(7, series);
    settings.put("Series", series);
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

  @Then("I upload {string} as presenter video")
  public void uploadPresenter(String filepath) {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"track_presenter\"]"));
    element.sendKeys(filepath);
    settings.put("Presenter", new File(filepath).getName());
  }

  @Then("I upload {string} as slide video")
  public void uploadSlides(String filepath) {
    WebElement element = driver.findElement(By.xpath("//*[@id=\"track_presentation\"]"));
    element.sendKeys(filepath);
    settings.put("Slides", new File(filepath).getName());
  }

  @Then("I set the start time to {int}{int}{int}T{int}:{int}:{int}Z")
  public void setUploadStartTime(int year, int month, int date, int hour, int minute, int second) {
    throw new PendingException();
  }

  @Then("I select {string} in the new event workflow dropdown")
  public void setWorkflow(String workflow) {
    setWorkflowDropdownContent(workflow);
    settings.put("Workflow", workflow);
  }

  @Then("I set the {string} workflow flag to {string}")
  public void setWorkflowFlat(String flagId, String value) {
    Boolean v = Boolean.valueOf(value);
    WebElement element = driver.findElement(By.id(flagId));
    if (element.isSelected() != v) {
      element.click();
    }
    settings.put(flagId, value);
  }

  @Then("I select {string} in the new event acl dropdown")
  public void setAcl(String acl) {
    setAclDropdownContent(acl);
    //TODO: Need to store the generated ACL, not the name since that doesn't appear in the check modal
  }

  @Then("I verify my selections on the confirmation modal")
  public void verify() {
    new WebDriverWait(driver, 2).until(ExpectedConditions.elementToBeClickable(By.linkText("Create")));
    for (Map.Entry<String, String> e : settings.entrySet()) {
      String baseXPath = "//*[@id=\"add-event-modal\"]//*[contains(text(), \"" + e.getKey() + "\")]";
      WebElement key = driver.findElement(By.xpath(baseXPath));
      WebElement value = key.findElement(By.xpath(baseXPath + "/../td[2]"));
      assertTrue("Key " + e.getKey() + " does not match, found " + value.getText() + " instead", e.getValue().equals(value.getText()));
    }
  }
}
