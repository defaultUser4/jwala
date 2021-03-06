package com.cerner.jwala.ui.selenium.steps.configuration;

import com.cerner.jwala.ui.selenium.component.JwalaUi;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Properties;

/**
 * Created by Jedd Cuison on 6/27/2017
 */
public class CreateWebServerRunSteps {

    @Autowired
    private JwalaUi jwalaUi;


    @Autowired
    @Qualifier("parameterProperties")
    private Properties paramProp;

    @Given("^I am in the web server tab$")
    public void goToWebServersTab() {
        jwalaUi.clickTab("Web Servers");
    }

    @When("^I click the add web server button$")
    public void clickAddWebServerBtn() {
        jwalaUi.click(By.xpath("//button[span[text()='Add']]"));
    }

    @And("^I see the web server add dialog$")
    public void checkAddWebServerDialogBoxIsDisplayed() {
        jwalaUi.waitUntilElementIsVisible(By.xpath("//span[text()='Add Web Server']"));
    }

    @And("^I fill in the \"Web Server Name\" field with \"(.*)\"$")
    public void setWebServerName(final String webServerName) {
        jwalaUi.sendKeys(By.name("webserverName"), webServerName);
    }

    @And("^I fill in the \"Host Name\" field with \"(.*)\"$")
    public void setHostName(final String hostName) {
        String actualHostName = paramProp.getProperty(hostName) == null ? hostName : paramProp.getProperty(hostName);
        jwalaUi.sendKeys(By.name("hostName"), actualHostName);
    }

    @And("^I fill in the \"Status Path\" field with \"(.*)\"$")
    public void setStatusPath(final String statusPath) {
        jwalaUi.getWebElement(By.name("statusPath")).sendKeys(Keys.chord(Keys.CONTROL, "a"), statusPath);
    }

    @And("^I fill in the \"HTTP Port\" field with \"(.*)\"$")
    public void setHttpPort(final String httpPort) {
        jwalaUi.sendKeys(By.name("portNumber"), httpPort);
    }

    @And("^I fill in the \"HTTPS Port\" field with \"(.*)\"$")
    public void setHttpsPort(final String httpsPort) {
        jwalaUi.sendKeys(By.name("httpsPort"), httpsPort);
    }

    @And("^I select the \"Status Path\" field$")
    public void selectStatusPath() {
        jwalaUi.click(By.name("statusPath"));
    }

    @And("^I select the \"Apache HTTPD\" field \"(.*)\"$")
    public void selectApacheHttpd(final String apacheHttpd) {
        jwalaUi.selectItem(By.name("apacheHttpdMediaId"), apacheHttpd);
    }

    @And("^I select the group \"(.*)\"$")
    public void selectGroup(final String groupName) {
        jwalaUi.click(By.xpath("//div[contains(text(), '" + groupName + "')]/input"));
    }

    @And("^I click the add web server dialog ok button$")
    public void clickAddWebServerDialogOkBtn() throws InterruptedException {
        jwalaUi.clickOkWithSpan();
    }

    @Then("^I see \"(.*)\" in the webserver table$")
    public void checkForWebServer(final String webServerName) {
        jwalaUi.waitUntilElementIsVisible(By.xpath("//button[text()='" + webServerName + "']"), 60);
    }
}
