package org.opencastproject.cucumber;

import static org.junit.Assert.assertTrue;
import static org.opencastproject.cucumber.ModalCommonSteps.setAclDropdownContent;
import static org.opencastproject.cucumber.ModalCommonSteps.setMetadataDropdownContent;
import static org.opencastproject.cucumber.ModalCommonSteps.setTextRowContent;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.LinkedHashMap;
import java.util.Map;

import cucumber.api.PendingException;
import cucumber.api.java.en.Then;

public class SeriesSteps {

  private static final String MODAL_TYPE = "series";

  private final WebDriver driver = WebDriverFactory.createWebDriver();

  private LinkedHashMap<String, String> settings = new LinkedHashMap<>();

  @Then("I set the title to {string} in the series modal")
  public void setTitle(String title) {
    setTextRowContent(MODAL_TYPE, 1, title);
    settings.put("Title", title);
  }

  @Then("I set the subject to {string} in the series modal")
  public void setSubject(String subject) {
    setTextRowContent(MODAL_TYPE, 2, subject);
    settings.put("Subject", subject);
  }

  @Then("I set the description to {string} in the series modal")
  public void setDescription(String description) {
    setTextRowContent(MODAL_TYPE, 3, description, "textarea");
    settings.put("Description", description);
  }

  @Then("I set the language dropdown to {string} in the series modal")
  public void setLanguage(String language) {
    setMetadataDropdownContent(MODAL_TYPE, 4, language);
    settings.put("Language", language);
  }

  @Then("I set the rights to {string} in the series modal")
  public void setRights(String rights) {
    setTextRowContent(MODAL_TYPE, 5, rights);
    settings.put("Rights", rights);
  }

  @Then("I set the license to {string} in the series modal")
  public void setLicense(String license) {
    setMetadataDropdownContent(MODAL_TYPE, 6, license);
    settings.put("License", license);
  }

  @Then("I set the presenter to {string} in the series modal")
  public void setPresenter(String presenter) {
    throw new PendingException("This UI element isn't quite a normal dropdown");
    //setTextRowContent(8, presenter);
  }

  @Then("I set the presenters to {string}, {string}, and {string} in the series modal")
  public void setPresenters(String a, String b, String c) {
    throw new PendingException("Need to implement this still");
  }

  @Then("I set the contributor to {string} in the series modal")
  public void setContributor(String contributor) {
    throw new PendingException("This UI element isn't quite a normal dropdown");
    //setTextRowContent(8, contributor);
  }

  @Then("I set the contributors to {string}, {string}, and {string} in the series modal")
  public void setContributors(String a, String b, String c) {
    throw new PendingException("Need to implement this still");
  }

  @Then("I set the publisher to {string} in the series modal")
  public void setPublisher(String publisher) {
    throw new PendingException("Need to implement this still");
  }

  @Then("I set the publishers to {string}, {string}, and {string} in the series modal")
  public void setPublishers(String a, String b, String c) {
    throw new PendingException("Need to implement this still");
  }

  @Then("I select {string} in the new series acl dropdown")
  public void setAcl(String acl) {
    String seriesACLXpath = "//*[@id=\"add-" + MODAL_TYPE + "-modal\"]/admin-ng-wizard/ng-include/div[3]/div/div/ul/li/div/div[1]/div/table/tbody/tr/td/div/div";
    setAclDropdownContent(seriesACLXpath, acl);
    //TODO: Need to store the generated ACL, not the name since that doesn't appear in the check modal
  }

  @Then("I select the {string} theme in the series modal")
  public void setTheme(String theme){
    throw new PendingException("Need to implement this still");
  }

  @Then("I verify my selections on the series confirmation modal")
  public void verify() {
    new WebDriverWait(driver, 2).until(ExpectedConditions.elementToBeClickable(By.linkText("Create")));
    for (Map.Entry<String, String> e : settings.entrySet()) {
      String baseXPath = "//*[@id=\"add-series-modal\"]//*[contains(text(), \"" + e.getKey() + "\")]";
      WebElement key = driver.findElement(By.xpath(baseXPath));
      WebElement value = key.findElement(By.xpath(baseXPath + "/../td[2]"));
      assertTrue("Key " + e.getKey() + " does not match, found " + value.getText() + " instead", e.getValue().equals(value.getText()));
    }
  }
}
