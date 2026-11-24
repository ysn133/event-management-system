package com.eventflow.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.support.ui.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaJourneysTest {
    WebDriver driver;
    WebDriverWait wait;
    JsonNode settings,fixture;
    final Path root=Path.of("..").toAbsolutePath().normalize();
    @BeforeEach void open() throws Exception {
        settings=new ObjectMapper().readTree(root.resolve(".runtime/settings.json").toFile());
        fixture=new ObjectMapper().readTree(root.resolve(".runtime/fixture.json").toFile());
        System.setProperty("webdriver.chrome.driver","/usr/bin/chromedriver");
        ChromeOptions options=new ChromeOptions();
        options.setBinary("/usr/bin/google-chrome");
        options.addArguments("--headless=new","--no-sandbox","--disable-dev-shm-usage","--window-size=1440,1000","--disable-background-networking");
        driver=new ChromeDriver(options);wait=new WebDriverWait(driver,Duration.ofSeconds(20));
    }
    @AfterEach void close(TestInfo info)throws Exception{if(driver!=null){snap("last-"+info.getTestMethod().orElseThrow().getName());driver.quit();}}
    WebElement visible(By by){return wait.until(ExpectedConditions.visibilityOfElementLocated(by));}
    WebElement button(String text){return wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(normalize-space(.),\""+text+"\")]")));}
    void snap(String name)throws Exception{
        Path dir=root.resolve("results/screenshots");Files.createDirectories(dir);
        Files.write(dir.resolve(name+".png"),((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES));
    }
    void login(){
        driver.get(settings.get("origin").asText()+"/evenements");
        visible(By.id("login-email")).sendKeys(settings.get("email").asText());
        driver.findElement(By.id("login-password")).sendKeys(settings.get("password").asText());
        button("Se connecter").click();
        wait.until(d->d.findElements(By.id("login-email")).isEmpty());
    }
    @Test void protectedPageRequiresLogin()throws Exception{
        driver.get(settings.get("origin").asText()+"/budgets");
        assertTrue(visible(By.id("login-email")).isDisplayed());
        assertTrue(driver.findElement(By.id("login-password")).isDisplayed());
        snap("selenium-01-auth-required");
    }
    @Test void invalidLoginShowsControlledError()throws Exception{
        driver.get(settings.get("origin").asText());
        visible(By.id("login-email")).sendKeys(settings.get("email").asText());
        driver.findElement(By.id("login-password")).sendKeys("quality-invalid-password");
        button("Se connecter").click();
        assertTrue(visible(By.cssSelector("[role='alert']")).getText().contains("incorrect"));
        // Clear the masked field before saving the screenshot.
        driver.findElement(By.id("login-password")).clear();
        snap("selenium-02-login-rejected");
    }
    @Test void createEventThroughRealForm()throws Exception{
        login();driver.get(settings.get("origin").asText()+"/evenements");
        button("Créer un événement").click();
        String title="Selenium — événement "+System.currentTimeMillis();
        visible(By.name("title")).sendKeys(title);
        driver.findElement(By.name("location")).sendKeys("Agadir — test isolé");
        for(String[] value:new String[][]{{"date","2027-04-12T09:00"},{"end","2027-04-12T18:00"}})
            ((JavascriptExecutor)driver).executeScript("arguments[0].value=arguments[1];arguments[0].dispatchEvent(new Event('input',{bubbles:true}));",driver.findElement(By.name(value[0])),value[1]);
        WebElement description=driver.findElement(By.cssSelector("textarea[name='description']"));
        ((JavascriptExecutor)driver).executeScript("arguments[0].scrollIntoView({block:'center'});",description);
        wait.until(ExpectedConditions.elementToBeClickable(description)).sendKeys("Création par Selenium Java sur base de test dédiée.");
        button("Créer l’événement").click();
        wait.until(d->d.findElements(By.name("title")).isEmpty());
        boolean immediate=driver.findElement(By.tagName("body")).getText().contains(title);
        Files.writeString(root.resolve("results/event-list-refresh-observation.json"),"{\"visibleImmediately\":"+immediate+",\"createdTitle\":\""+title+"\"}");
        driver.navigate().refresh();
        boolean found=wait.until(d->d.findElement(By.tagName("body")).getText().contains(title));
        assertTrue(found);
        snap("selenium-03-event-created");
    }
    @Test void addExpenseAndOpenItsDetails()throws Exception{
        login();driver.get(settings.get("origin").asText()+"/budgets");
        Select events=new Select(visible(By.cssSelector(".event-select select")));
        events.selectByValue(fixture.get("eventId").asText());
        wait.until(d->d.findElement(By.tagName("body")).getText().contains("Qualité Java — démonstration"));
        button("Ajouter une dépense").click();
        String label="Dépense Selenium "+System.currentTimeMillis();
        visible(By.cssSelector("input[name='description']")).sendKeys(label);
        driver.findElement(By.name("category")).sendKeys("Technique");
        driver.findElement(By.name("amount")).sendKeys("350");
        button("Ajouter la dépense").click();
        wait.until(d->d.findElements(By.name("amount")).isEmpty());
        By expense=By.xpath("//button[contains(@class,'expense-link') and contains(.,'"+label+"')]");
        wait.until(ExpectedConditions.elementToBeClickable(expense)).click();
        WebElement dialog=visible(By.cssSelector("[role='dialog']"));
        assertTrue(dialog.getText().contains(label));
        assertTrue(dialog.getText().contains("Technique"));
        snap("selenium-04-expense-details");
    }
}
