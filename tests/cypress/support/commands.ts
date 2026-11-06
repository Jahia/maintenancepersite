// Project-specific custom Cypress commands go here.
// @jahia/cypress already registers cy.login / cy.logout / cy.apollo / cy.executeGroovy / etc.
// via registerSupport() in support/e2e.js, but its helpers (e.g. createSite) call
// cy.waitUntil, which is provided by the cypress-wait-until plugin registered here.
import 'cypress-wait-until'
