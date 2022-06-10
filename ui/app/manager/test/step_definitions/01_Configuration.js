const { When, Then } = require("@cucumber/cucumber");
const { expect } = require("@playwright/test");

/**
 * add new user
 */
Then('Switch to {string} realm', async function (realm) {
    await this.switchToRealmByRealmPicker(realm)
})

Then("Add a new user", async function () {

    // type in username
    await this.click('.mdi-plus >> nth=0')
    await this.fill('input[type="text"] >> nth=0', 'smartcity')

    // type in password
    await this.fill('#password-user0 input[type="password"]', 'smartcity')
    await this.fill('#repeatPassword-user0 input[type="password"]', 'smartcity')

    // select permissions
    await this.click('div[role="button"]:has-text("Roles")');
    await this.click('li[role="menuitem"]:has-text("Read")');
    await this.click('li[role="menuitem"]:has-text("Write")');
    await this.click('div[role="button"]:has-text("Roles")')
    await this.wait(500)

    //create
    await this.click('button:has-text("create")')
})

Then('We see a new user', async function () {
    await this.wait(500)
    const count = await this.count('td:has-text("smartcity")')
    //const count = await page.locator('td:has-text("smartcity")').count()
    await expect(count).toBe(1)
})

/**
 * add role
 */

Then('Create a new role', async function () {
    const { page } = this;

    await this.click('text=Add Role')

    // get total number of current roles
    let rows = await page.$$('.mdc-data-table__row')
    const count = await rows.length

    await this.fill(`#attribute-meta-row-${count - 1} input[type="text"] >> nth=0`, 'Custom');
    //await page.locator(`#attribute-meta-row-${count - 1} input[type="text"]`).first().fill('Custom');
    await this.fill(`#attribute-meta-row-${count - 1} input[type="text"] >> nth=1`, 'read:asset, write:asset');
    //await page.locator(`#attribute-meta-row-${count - 1} input[type="text"]`).nth(1).fill('read:asset, write:asset');
    
    await this.check(`#attribute-meta-row-${count - 1} td .meta-item-container div:nth-child(2) div or-mwc-input:nth-child(3) #field #component #elem >> nth=0`)
    //await page.locator(`#attribute-meta-row-${count - 1} td .meta-item-container div:nth-child(2) div or-mwc-input:nth-child(3) #field #component #elem`).first().check();
    
    await this.check(`#attribute-meta-row-${count - 1} td .meta-item-container div:nth-child(2) div:nth-child(2) or-mwc-input:nth-child(3) #field #component #elem`)
    //await page.locator(`#attribute-meta-row-${count - 1} td .meta-item-container div:nth-child(2) div:nth-child(2) or-mwc-input:nth-child(3) #field #component #elem`).check();
    await this.click('button:has-text("create")')
})


Then('We see a new role', async function () {
    await this.wait(500)
    const count = await this.count('text=Custom')
    //const count = await page.locator('text=Custom').count()
    await expect(count).toBe(1)
})

/**
 * apply new role
 */
Then('Select the new role and unselect others', async function () {

    await this.click('td:has-text("smartcity")')
    await this.click('div[role="button"]:has-text("Roles")');
    await this.click('li[role="menuitem"]:has-text("Read")');
    await this.click('li[role="menuitem"]:has-text("Write")');
    await this.click('li[role="menuitem"]:has-text("Custom")')
    await this.wait(300)
    await this.press("Enter")
})

Then('We see that assets permission are selected', async function () {

    //we expect to see two checkbox selected and disabled

    const { page } = this;
    var checkboxes = await page.$$('.mdc-checkbox__native-control')

    // third one is read asset 
    const readAsset_checked = await checkboxes[2].isChecked()
    const readAsset_disabled = await checkboxes[2].isDisabled()
    await expect(readAsset_checked).toBeTruthy()
    await expect(readAsset_disabled).toBeTruthy()

    // ninth one is write asset
    const writeAsset_checked = await checkboxes[8].isChecked()
    const writeAsset_disabled = await checkboxes[8].isDisabled()
    await expect(writeAsset_checked).toBeTruthy()
    await expect(writeAsset_disabled).toBeTruthy()
})

Then('Switch back to origin', async function () {

    await this.click('text=Roles Custom')
    await this.click('li[role="menuitem"]:has-text("Read")');
    await this.click('li[role="menuitem"]:has-text("Write")');
    await this.click('li[role="menuitem"]:has-text("Custom")')
    await this.wait(200)
    await this.press("Enter")
})

/**
 * switch user
 */
When('Logout', async function () {
    await this.logout();
})
