package org.opencastproject.cucumber;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class WebDriverFactory {
  public static WebDriver createWebDriver() {
    String webdriver = System.getProperty("browser", "firefox");
    switch(webdriver) {
      case "firefox":
        return new FirefoxDriver();
      case "chrome":
        return new ChromeDriver();
      default:
        throw new RuntimeException("Unsupported webdriver: " + webdriver);
    }
  }
}