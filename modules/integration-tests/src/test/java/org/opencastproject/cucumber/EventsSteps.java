package org.opencastproject.cucumber;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opencastproject.cucumber.ModalCommonSteps.setAclDropdownContent;
import static org.opencastproject.cucumber.ModalCommonSteps.setDropdownContentAndVerify;
import static org.opencastproject.cucumber.ModalCommonSteps.setMetadataDropdownContent;
import static org.opencastproject.cucumber.ModalCommonSteps.setTextRowContent;
import static org.opencastproject.cucumber.WebDriverFactory.getDriver;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cucumber.api.PendingException;
import cucumber.api.java.en.Then;

public class EventsSteps {

  private static final String MODAL_TYPE = "event";

  private LinkedHashMap<String, String> settings = new LinkedHashMap<>();

  @Then("I set the title to {string} in the event modal")
  public void setTitle(String title) {
    setTextRowContent(MODAL_TYPE, 1, title);
    settings.put("Title", title);
  }

  @Then("I set the subject to {string} in the event modal")
  public void setSubject(String subject) {
    setTextRowContent(MODAL_TYPE, 2, subject);
    settings.put("Subject", subject);
  }

  @Then("I set the description to {string} in the event modal")
  public void setDescription(String description) {
    setTextRowContent(MODAL_TYPE, 3, description, "textarea");
    settings.put("Description", description);
  }

  @Then("I set the language dropdown to {string} in the event modal")
  public void setLanguage(String language) {
    setMetadataDropdownContent(MODAL_TYPE, 4, language);
    settings.put("Language", language);
  }

  @Then("I set the rights to {string} in the event modal")
  public void setRights(String rights) {
    setTextRowContent(MODAL_TYPE, 5, rights);
    settings.put("Rights", rights);
  }

  @Then("I set the license to {string} in the event modal")
  public void setLicense(String license) {
    setMetadataDropdownContent(MODAL_TYPE, 6, license);
    settings.put("License", license);
  }

  @Then("I set the series to {string} in the event modal")
  public void setSeries(String series) {
    setMetadataDropdownContent(MODAL_TYPE, 7, series);
    settings.put("Series", series);
  }

  @Then("I set the presenter to {string} in the event modal")
  public void setPresenter(String presenter) {
    throw new PendingException("This UI element isn't quite a normal dropdown");
    //setTextRowContent(8, presenter);
  }

  @Then("I set the presenters to {string}, {string}, and {string} in the event modal")
  public void setPresenters(String a, String b, String c) {
    throw new PendingException("Need to implement this still");
  }

  @Then("I set the contributor to {string} in the event modal")
  public void setContributor(String contributor) {
    throw new PendingException("This UI element isn't quite a normal dropdown");
    //setTextRowContent(8, contributor);
  }

  @Then("I set the contributors to {string}, {string}, and {string} in the event modal")
  public void setContributors(String a, String b, String c) {
    throw new PendingException("Need to implement this still");
  }

  @Then("I upload {string} as presenter video")
  public void uploadPresenter(String filepath) {
    WebElement element = getDriver().findElement(By.xpath("//*[@id=\"track_presenter\"]"));
    element.sendKeys(filepath);
    settings.put("Presenter", new File(filepath).getName());
  }

  @Then("I upload {string} as slide video")
  public void uploadSlides(String filepath) {
    WebElement element = getDriver().findElement(By.xpath("//*[@id=\"track_presentation\"]"));
    element.sendKeys(filepath);
    settings.put("Slides", new File(filepath).getName());
  }

  @Then("I set the start time to {int}{int}{int}T{int}:{int}:{int}Z")
  public void setUploadStartTime(int year, int month, int date, int hour, int minute, int second) {
    throw new PendingException();
  }

  //Note: This only works with Chosen!
  @Then("I select {string} in the new event workflow dropdown")
  public void setWorkflow(String workflow) {
    String rawXPath = "//*[@id=\"add-" + MODAL_TYPE + "-modal\"]/admin-ng-wizard/ng-include/div[5]/div/div/div/div/div[1]";
    String inputXPath = rawXPath + "/div/ul/li[text()=\"" + workflow + "\"]";
    String renderedXPath = rawXPath + "/a/span";
    setDropdownContentAndVerify(rawXPath, inputXPath, renderedXPath, "Select workflow", workflow);

    settings.put("Workflow", workflow);
  }

  @Then("I set the {string} workflow flag to {string}")
  public void setWorkflowFlat(String flagId, String value) {
    Boolean v = Boolean.valueOf(value);
    WebElement element = getDriver().findElement(By.id(flagId));
    if (element.isSelected() != v) {
      element.click();
    }
    settings.put(flagId, value);
  }

  @Then("I select {string} in the new event acl dropdown")
  public void setAcl(String acl) {
    String eventACLXpath = "//*[@id=\"add-" + MODAL_TYPE+ "-modal\"]/admin-ng-wizard/ng-include/div[6]/div/div/ul[2]/li/div/div[1]/div/table/tbody/tr/td/div/div";
    setAclDropdownContent(eventACLXpath, acl);
    //TODO: Need to store the generated ACL, not the name since that doesn't appear in the check modal
  }

  @Then("I verify my selections on the event confirmation modal")
  public void verify() {
    new WebDriverWait(getDriver(), 2).until(ExpectedConditions.elementToBeClickable(By.linkText("Create")));
    for (Map.Entry<String, String> e : settings.entrySet()) {
      String baseXPath = "//*[@id=\"add-event-modal\"]//*[contains(text(), \"" + e.getKey() + "\")]";
      WebElement key = getDriver().findElement(By.xpath(baseXPath));
      WebElement value = key.findElement(By.xpath(baseXPath + "/../td[2]"));
      assertTrue("Key " + e.getKey() + " does not match, found " + value.getText() + " instead", e.getValue().equals(value.getText()));
    }
  }

  @Then("I wait until the event has uploaded")
  public void waitUntilUploaded() {
    String base = "/html/body/section/div/ul/li";
    By target = By.xpath(base);
    By notification = By.xpath(base + "/div/p");
    By closeButton = By.xpath(base + "/div/a");

    String uploading = "The event is being uploaded…";
    String uploaded = "The event has been created";

    //Wait while the event uploads
    WebElement element = new WebDriverWait(getDriver(), 2)
            .until(ExpectedConditions.visibilityOfElementLocated(notification));
    assertTrue("Notification element should be visible", element.isDisplayed());
    //We check against both uploading and uploaded because sometimes the upload is already done by the time we check!
    assertTrue("Event upload notification text incorrect",
            element.getText().equals(uploading) || element.getText().equals(uploaded));

    //Wait for the event upload completion notice to show up
    new WebDriverWait(getDriver(), 120)
            .until(ExpectedConditions.textToBePresentInElementLocated(notification, uploaded ));
    element = getDriver().findElement(notification);
    assertTrue("Event upload completion notification text incorrect", element.getText().equals(uploaded));

    element = getDriver().findElement(closeButton);
    element.click();
    assertFalse("Closing the notification did not make it disappear", element.isDisplayed());

    //Wait until the notice fades out, and ensure it actually does
    new WebDriverWait(getDriver(), 10)
            .until(ExpectedConditions.numberOfElementsToBe(target, 0));
    List<WebElement> elements = getDriver().findElements(target);
    assertTrue("Notification element should be empty, instead has " + elements.size(), elements.size() == 0);
  }
}
