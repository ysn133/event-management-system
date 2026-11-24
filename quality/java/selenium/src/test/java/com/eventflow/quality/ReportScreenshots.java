package com.eventflow.quality;

import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;

class ReportScreenshots {
    @Test void captureNativeReports() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        System.setProperty("webdriver.chrome.driver","/usr/bin/chromedriver");
        ChromeOptions options=new ChromeOptions();options.setBinary("/usr/bin/google-chrome");
        options.addArguments("--headless=new","--no-sandbox","--disable-dev-shm-usage","--window-size=1440,1050");
        WebDriver driver=new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(25));
        try {
            for(String[] page:new String[][]{
                {"index.html","quality-summary"},
                {"budget-service/site/jacoco/index.html","jacoco-budget"},
                {"budget-service/site/checkstyle.html","checkstyle-budget"},
                {"postman.html","postman-native"},
                {"performance.html","jmeter-results"},
                {"snyk.html","snyk-results"},
                {"zap.html","zap-native"}
            }) {
                driver.get("http://127.0.0.1:46380/quality-reports/"+page[0]);
                Files.write(root.resolve("results/screenshots/"+page[1]+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
            }
        } finally {driver.quit();}
    }
}
