import {
    addNode,
    addMixins,
    removeMixins,
    createSite,
    deleteSite,
    enableModule,
    publishAndWaitJobEnding,
} from '@jahia/cypress'

const siteKey = 'maintenancepersite-test'
const MODULE_NAME = 'maintenancepersite'
const MAINTENANCE_PAGE_TITLE = 'Site under maintenance'
// The title also appears in the site navigation of every page, so match the page's own <title>:
// it proves the maintenance page itself was rendered, with its page template.
const MAINTENANCE_PAGE_TITLE_TAG = new RegExp(`<title>\\s*${MAINTENANCE_PAGE_TITLE}\\s*</title>`)

describe('Maintenance per site', () => {
    let maintenancePageUuid: string

    before('Create test site and maintenance page', () => {
        cy.login()
        createSite(siteKey, { templateSet: 'dx-base-demo-templates', locale: 'en', serverName: 'localhost' })
        enableModule(MODULE_NAME, siteKey)
        addNode({
            parentPathOrId: `/sites/${siteKey}/home`,
            name: 'maintenance',
            primaryNodeType: 'jnt:page',
            properties: [
                { name: 'jcr:title', value: MAINTENANCE_PAGE_TITLE, language: 'en' },
                // dx-base-demo-templates has no 'default' page template: with it the page renders as a 404,
                // so the 503 body was empty whatever the module did.
                { name: 'j:templateName', value: '2-column' },
            ],
        }).then((response) => {
            maintenancePageUuid = response.data.jcr.addNode.uuid
        })
        publishAndWaitJobEnding(`/sites/${siteKey}`)
        cy.logout()
    })

    after('Delete test site', () => {
        cy.login()
        deleteSite(siteKey)
        cy.logout()
    })

    it('serves the home page normally when the site is not in maintenance', () => {
        cy.request(`/sites/${siteKey}/home.html`).its('status').should('eq', 200)
    })

    // Keep this test before the direct request below: it then renders the maintenance page for the first
    // time, not from the HTML cache, which is the case where a nested render after the page came out empty.
    it('serves a 503 with the maintenance page once jmix:maintenancePerSite is enabled', () => {
        cy.login()
        addMixins(`/sites/${siteKey}`, ['jmix:maintenancePerSite'])
        cy.apollo({
            mutationFile: 'graphql/mutation/setMaintenancePage.graphql',
            variables: { pathOrId: `/sites/${siteKey}`, value: maintenancePageUuid },
        })
        publishAndWaitJobEnding(`/sites/${siteKey}`)
        cy.logout()

        cy.request({ url: `/sites/${siteKey}/home.html`, failOnStatusCode: false }).then((response) => {
            expect(response.status).to.eq(503)
            expect(response.headers['retry-after']).to.exist
            expect(response.headers['cache-control']).to.contain('no-store')
            expect(response.body).to.match(MAINTENANCE_PAGE_TITLE_TAG)
        })
    })

    it('keeps the maintenance page itself reachable (no redirect loop)', () => {
        cy.request(`/sites/${siteKey}/home/maintenance.html`).then((response) => {
            expect(response.status).to.eq(200)
            expect(response.body).to.match(MAINTENANCE_PAGE_TITLE_TAG)
        })
    })

    it('restores normal output once the mixin is removed', () => {
        cy.login()
        removeMixins(`/sites/${siteKey}`, ['jmix:maintenancePerSite'])
        publishAndWaitJobEnding(`/sites/${siteKey}`)
        cy.logout()

        cy.request(`/sites/${siteKey}/home.html`).its('status').should('eq', 200)
    })
})
