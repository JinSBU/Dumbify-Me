import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.security.auth.login.LoginException;
import java.util.Collections;

import static java.lang.Thread.sleep;

public class DiscordBot extends ListenerAdapter {

    static ChromeOptions options;
    static WebDriver driver;
    static String email = "";
    static String password = "";
    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        if(event.getAuthor().isBot()){
            return;
        }
        System.out.println("Received message from " + event.getAuthor().getName() + ": " + event.getMessage().getContentDisplay());
        if(event.getMessage().getContentRaw().contains("!chegg")){
            String[] messageContents = event.getMessage().getContentRaw().split(" ");
            String cheggLink = messageContents[1];
            String answer = null;
            try {
                event.getChannel().sendMessage("Fetching your answer...").queue();
                answer = getAnswer(driver, cheggLink);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(answer.length() > 2000){
                int i = 0;
                while(i < answer.length()){
                    if(i > answer.length()){
                        i = answer.length();
                    }
                    String content = answer.substring(i, i+2000);
                    event.getChannel().sendMessage(content).queue();
                    i+=2000;
                }
            }
            else{
                event.getChannel().sendMessage(answer).queue();
            }

        }
    };

    public static void main(String[] args) throws InterruptedException, LoginException {
        options = new ChromeOptions();
        options.addArguments("--start-maximized", "--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.50 Safari/537.36");

        options.setExperimentalOption("useAutomationExtension", false);
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));

        System.setProperty("webdriver.chrome.driver", "C:/Users/jinth/IdeaProjects/Flight-Price-Checker/chromedriver.exe");
        driver = new ChromeDriver(options);
        launch();
    }
    public static void loginGoogle(WebDriver driver, String email, String password) throws InterruptedException {
        driver.navigate().to("https://gmail.com");
        waitForLoad(driver);
        sleep(2000);
        WebElement emailBox = driver.findElement(By.id("identifierId"));
        emailBox.sendKeys(email, Keys.ENTER);
        sleep(3000);
        WebElement passwordBox = driver.findElement(By.name("password"));
        passwordBox.sendKeys(password, Keys.ENTER);
        sleep(2000);
    }
    public static void waitForLoad(WebDriver driver) {
        new WebDriverWait(driver, 30).until((ExpectedCondition<Boolean>) wd ->
                ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete"));
    }
    public static String getAnswer(WebDriver driver, String cheggLink) throws InterruptedException {
        String pageSource, returnValue = "";
        driver.navigate().to("");
        sleep(2000);
        ((JavascriptExecutor)driver).executeScript("window.open();");
        String parentHandle = driver.getWindowHandle(); // get the current window handle
              //Clicking on this window
        for (String winHandle : driver.getWindowHandles()) { //Gets the new window handle
            driver.switchTo().window(winHandle);        // switch focus of WebDriver to the next found window handle (that's your newly opened window)
        }
        driver.get("https://www.youtube.com/");
        sleep(500);
        WebElement searchBox = driver.findElement(By.id("search"));
        searchBox.sendKeys(cheggLink, Keys.chord(Keys.CONTROL, "a"), Keys.chord(Keys.CONTROL, "c"));
        driver.close();
        driver.switchTo().window(parentHandle);



        WebElement inputBox = driver.findElement(By.id("1"));

        inputBox.sendKeys(Keys.chord(Keys.CONTROL, "v"), Keys.ENTER);

        try{
            WebDriverWait wait = new WebDriverWait(driver, 15);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("blur")));
            pageSource = driver.getPageSource();
            returnValue = parsePageSource(pageSource);
//            System.out.println(returnValue);
        }
        catch (Exception e){
            returnValue = "Loading took too long. Try again maybe?";
        }
        return returnValue;
    }
    public static void launch() throws LoginException, InterruptedException {
        JDABuilder builder = new JDABuilder((AccountType.BOT));
        String token = "";
        builder.addEventListener(new DiscordBot());
        builder.setToken(token).build();
        loginGoogle(driver, email, password);

    }
    public static String parsePageSource(String pageSource){
//        System.out.println(pageSource);
        String removedHeader = pageSource.substring(pageSource.indexOf("<div id=\"content\">"));

        //Removes majority of the tags.
        String removedTags = removedHeader.replaceAll("<p>","").replaceAll("</p>", "").replaceAll(
                "<img alt=\"\">", "").replaceAll(" src=", "").replaceAll("/>", "").replaceAll("<div id=\"content\">","")
                .replaceAll("img alt=\"\"\"", "").replaceAll("\"", "").replaceAll("style=\"height:[^0-9]*px;width:[^0-9]*px;", "").replaceAll("<//", "");

        String result = removedTags.substring(0, removedTags.indexOf("</div>"));
        return result;
    }
}
