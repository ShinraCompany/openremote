const { When, Then } = require("@cucumber/cucumber");
const { expect } = require("@playwright/test");

/**
 * add new asset
 */
Then('Create a {string} with name of {string}', async function (asset, name) {
    await this.click('.mdi-plus')
    await this.click(`text=${asset}`)
    await this.fill('#name-input input[type="text"]', name)
    await this.click('#add-btn')
})

When('Go to asset {string} info page', { timeout: 10000 }, async function (name) {
    await this.unSelectAll()
    await this.click(`text=${name}`)
    await this.wait(300)
})

Then('Go to modify mode', { timeout: 10000 }, async function () {
    await this.click('button:has-text("Modify")')
})

Then('Give {string} to the {string} with type of {string}', async function (value, attribute, type) {
    await this.fill(`text=${attribute} ${type} >> input[type="number"]`, value)
})

Then('Save', async function () {

    // press enter to enable the save button (could be any other action) (or this will be fixed then no pre-action needed)
    await this.click('#edit-container')
    await this.click('button:has-text("Save")')
})

Then('We see the asset with name of {string}', async function (name) {
    const count = await this.count(`text=${name}`)
    //const count = await page.locator(`text=${name}`).count()

    // reason why it's 1 is because that this scnario runs in a outline
    // each time only one set of data will be used in one run of outlines
    // thus, only one asset will be added and removed in one run and next time will start with the empty envrioment
    await expect(count).toBe(1)
})

/**
 * select an asset
 */
When('Search for the {string}', async function (name) {

    await this.fill('#filterInput input[type="text"]', name)
})

When('Select the {string}', async function (name) {
    await this.click(`text=${name}`)
})

Then('We see the {string} page', async function (name) {
    const { page } = this;
    await expect(await page.waitForSelector(`#asset-header >> text=${name}`)).not.toBeNull()
})

/**
 * update
 */
Then('Update {string} to the {string} with type of {string}', { timeout: 7000 }, async function (value, attribute, type) {

    await this.fill(`#field-${attribute} input[type="${type}"]`, value)
    await this.click(`#field-${attribute} #send-btn span`)
})

Then('Update location of {int} and {int}', { timeout: 8000 }, async function (location_x, location_y) {
    const { page } = this;

    await this.click('text=location GEO JSON point >> button span')

    // location_x and location_y are given by the example data
    // currently it's not implmented as dragging the map and clicking on a random place (could be possible in the future)
    await page.mouse.click(location_x, location_y, { delay: 700 })
    await this.click('button:has-text("OK")')
})

/**
 * read only
 */
Then('Uncheck on readonly of {string}', async function (attribute) {
    await this.click(`td:has-text("${attribute}") >> nth=0`)

    // bad solution 
    // nth number is decided by the default state
    // if default stete changes, please change the nth number
    if (attribute == "energyLevel")
        await this.click('text=Read only >> nth=2')
    else
        await this.click('text=Read only >> nth=1')
})

Then('Check on readonly of {string}', async function (attribute) {

    await this.click(`td:has-text("${attribute}")`)

    // bad solution
    // in this case, i assume that the config items are as the beginning state, namely default state
    // if the default state changes, the following nth-chlid should change as well
    if (attribute == "efficiencyExport")
        await this.click('.item-add or-mwc-input #component >> nth=0')
    else
        await this.click('tr:nth-child(14) td .meta-item-container div .item-add or-mwc-input #component')
    await this.click('li[role="checkbox"]:has-text("Read only")')
    await this.click('div[role="alertdialog"] button:has-text("Add")')

})

When('Go to panel page', async function () {
    await this.click('button:has-text("View")')
})

Then('We should see a button on the right of {string}', async function (attribute) {
    const { page } = this;

    await expect(page.waitForSelector(`#field-${attribute} button`)).not.toBeNull()
})

Then('No button on the right of {string}', async function (attribute) {
    const count = await this.count(`#field-${attribute} button`)
    //const count = await page.locator(`#field-${attribute} button`).count()
    expect(count).toBe(0)
})

/**
 * set configure item
 */
Then('Select {string} and {string} on {string}', async function (item1, item2, attribute) {
    await this.configItem(item1, item2, attribute)
})