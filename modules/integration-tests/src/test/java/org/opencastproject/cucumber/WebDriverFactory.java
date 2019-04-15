package org.opencastproject.cucumber;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

import java.io.File;
import java.io.IOException;

import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;

public class WebDriverFactory {

  private static WebDriver driver;

  @Before
  public static WebDriver createWebDriver() {
    if (null != driver) {
      return driver;
    }
    String webdriver = System.getProperty("browser", "firefox");
    switch(webdriver) {
      case "firefox":
        driver = new FirefoxDriver();
        return driver;
      case "chrome":
        driver = new ChromeDriver();
        return driver;
      default:
        throw new RuntimeException("Unsupported webdriver: " + webdriver);
    }
  }

  public static WebDriver getDriver() {
    return createWebDriver();
  }

  @After
  public static void quit(Scenario scenario) throws IOException {
    if (scenario.isFailed()) {
      driver.manage().window().maximize();
      File scrFile = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
      // Now you can do whatever you need to do with it, for example copy somewhere
      FileUtils.copyFile(scrFile, new File(scenario.getName() + ".png"));
    }
    driver.quit();
    driver = null;
  }
}