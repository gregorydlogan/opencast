package org.opencastproject.cucumber;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.chrome.ChromeDriver;

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

  @After
  public static void quit() {
    driver.quit();
    driver = null;
  }
}