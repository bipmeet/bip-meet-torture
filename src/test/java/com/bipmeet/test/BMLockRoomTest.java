/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bipmeet.test;

import org.jitsi.meet.test.LockRoomTest;
import org.jitsi.meet.test.base.JitsiMeetUrl;
import org.jitsi.meet.test.pageobjects.web.ModalDialogHelper;
import org.jitsi.meet.test.pageobjects.web.SecurityDialog;
import org.jitsi.meet.test.util.MeetUIUtils;
import org.jitsi.meet.test.util.MeetUtils;
import org.jitsi.meet.test.util.TestUtils;
import org.jitsi.meet.test.web.WebParticipant;
import org.jitsi.meet.test.web.WebTestBase;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * 1. Lock the room (make sure the image changes to locked)
 * 2. Join with a second browser/tab
 * 3. Make sure we are required to enter a password.
 * (Also make sure the padlock is locked)
 * 4. Enter wrong password, make sure we are not joined in the room
 * 5. Unlock the room (Make sure the padlock is unlocked)
 * 6. Join again and make sure we are not asked for a password and that
 * the padlock is unlocked.
 *
 * @author Damian Minkov
 * @author Leonard Kim
 */
public class BMLockRoomTest extends LockRoomTest {
    public static String ROOM_KEY = null;
    public static int PASSWORD_SIZE;
    public static final String MODERATOR_TOKEN_PNAME = "org.jitsi.moderated.room.token";
    public static final String LOCKED_ROOM_BLUE_KEY = "//div[@aria-label='Meeting Password' and @aria-pressed='LOCKED_LOCALLY']";

    @Override
    public void setupClass()
    {
        String token = getStringConfigValue(MODERATOR_TOKEN_PNAME);

        if ( token == null || token.trim().length() == 0)
        {
            throw new SkipException("missing configurations");
        }

        JitsiMeetUrl url = getJitsiMeetUrl();

        ensureOneParticipant(url.copy().setRoomParameters("jwt=" + token));
    }

    /**
     * Stops the participant. And locks the room from participant1.
     */
    @Test
    public void lockRoom()
    {
        // just in case wait
        TestUtils.waitMillis(1000);

        participant1LockRoom();
    }

    /**
     * participant1 locks the room.
     */
    private void participant1LockRoom()
    {
        WebParticipant participant1 = getParticipant1();
        WebDriver driver = participant1.getDriver();
        participant1.moveToElement();
        participantSetPasswordForBipMeet();

        TestUtils.waitForDisplayedElementByXPath(driver, LOCKED_ROOM_BLUE_KEY, 5);


    }
    private void participantSetPasswordForBipMeet() {

        WebParticipant participant1 = getParticipant1();
        WebDriver driver = participant1.getDriver();

        WebElement passwordIcon = driver.findElement
                (By.xpath("//div[contains(@data-testid,'Meeting Password--container')]"));

        passwordIcon.click();

        List<WebElement> passwordBoxes = driver.findElements(By.xpath("//input[contains(@name,'enteredValue')]"));
        WebElement box;
        StringBuilder sb = new StringBuilder();
        PASSWORD_SIZE = passwordBoxes.size();

        for (int i = 0; i < PASSWORD_SIZE; i++) {
            int randomPw = (int) (Math.random() * 10);
            String pw = String.valueOf(randomPw);
            box = passwordBoxes.get(i);
            box.sendKeys("" + pw);

            sb.append(pw);
        }
        ROOM_KEY = sb.toString();

        ModalDialogHelper.clickOKButton(driver);
    }

    /**
     * first wrong pin then correct one
     */
    @Test(dependsOnMethods = {"lockRoom"})
    public void enterParticipantInLockedRoom() {
        WebParticipant participant1 = getParticipant1();
        WebDriver driver = participant1.getDriver();
        TestUtils.waitForDisplayedElementByXPath(driver, LOCKED_ROOM_BLUE_KEY, 5);


        joinSecondParticipant();

        WebParticipant participant2 = getParticipant2();
        WebDriver driver2 = participant2.getDriver();

        // wait for password prompt
        waitForPasswordDialog(driver2);

        //Fill with wrong password
        setBipMeetPassword(driver2, "111111", PASSWORD_SIZE);
        waitForPasswordDialog(driver2);

        //Fill with correct password
        setBipMeetPassword(driver2, ROOM_KEY, PASSWORD_SIZE);
        participant2.waitToJoinMUC(5);

        //assertTrue(driver2.findElement(By.xpath("//div[@aria-label='Meeting Password' and @aria-pressed='LOCKED_LOCALLY']")).isDisplayed());  ??
    }

    /**
     * Unlock room. Check whether room is still locked. Click remove and check
     * whether it is unlocked.
     */
    @Test(dependsOnMethods = {"enterParticipantInLockedRoom"})
    public void unlockRoom()
    {
        getParticipant2().hangUp();

        // just in case wait
        TestUtils.waitMillis(1000);

        WebParticipant participant1 = getParticipant1();
        WebDriver driver = participant1.getDriver();

        participant1.moveToElement();

        TestUtils.click(driver, By.xpath("//div[contains(@data-testid,'Meeting Password--container')]"));

        /**
         * New function added to ModalDialog.
         */
        ModalDialogHelper.clickSendButton(driver);


    }

    /**
     * Interacts with the password modal to enter and submit a password.
     *
     * @param driver   the participant that should be used to interact with the
     *                 password modal
     * @param password the password to enter and submit
     */


    public static void setBipMeetPassword(WebDriver driver, String password, Integer size) {
        waitForPasswordDialog(driver);
        List<WebElement> passwordBoxes = driver.findElements(By.xpath("//input[contains(@name,'enteredValue')]"));
        WebElement box;

        for (int i = 0; i < size; i++) {
            char pwCharacter = password.charAt(i);
            box = passwordBoxes.get(i);
            box.sendKeys("" + pwCharacter);
        }

        ModalDialogHelper.clickOKButton(driver);
    }

    /**
     * Waits till the password dialog is shown.
     *
     * @param driver the participant that should be used to interact with the
     *               password modal
     */
    public static void waitForPasswordDialog(WebDriver driver) {
        TestUtils.waitForElementBy(
                driver,
                By.xpath("//h3[text()='Please enter meeting password to join.']"),
                5);
    }
}
