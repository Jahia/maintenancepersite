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
                { name: 'j:templateName', value: 'default' },
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
            expect(response.body).to.contain(MAINTENANCE_PAGE_TITLE)
        })
    })

    it('keeps the maintenance page itself reachable (no redirect loop)', () => {
        cy.request(`/sites/${siteKey}/home/maintenance.html`).its('status').should('eq', 200)
    })

    it('restores normal output once the mixin is removed', () => {
        cy.login()
        removeMixins(`/sites/${siteKey}`, ['jmix:maintenancePerSite'])
        publishAndWaitJobEnding(`/sites/${siteKey}`)
        cy.logout()

        cy.request(`/sites/${siteKey}/home.html`).its('status').should('eq', 200)
    })
})
