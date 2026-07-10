// This file is processed and loaded automatically before your test files.
// https://on.cypress.io/configuration

import './commands'
import addContext from 'mochawesome/addContext'

// Ensure fetch is always bound to window
if (typeof window !== 'undefined' && window.fetch) {
    // eslint-disable-next-line no-undef
    globalThis.fetch = window.fetch.bind(window)
}

// eslint-disable-next-line @typescript-eslint/no-require-imports
require('cypress-terminal-report/src/installLogsCollector')()
// eslint-disable-next-line @typescript-eslint/no-require-imports
require('@jahia/cypress/dist/support/registerSupport').registerSupport()

Cypress.on('uncaught:exception', () => {
    // Returning false here prevents Cypress from failing the test on page errors
    // unrelated to the assertions being made (e.g. the 503 maintenance page).
    return false
})

Cypress.on('test:after:run', (test, runnable) => {
    let videoName = Cypress.spec.relative
    videoName = videoName.replace('/.cy.*', '').replace('cypress/e2e/', '')
    const videoUrl = 'videos/' + videoName + '.mp4'
    addContext({ test }, videoUrl)
    if (test.state === 'failed') {
        const screenshot = `screenshots/${Cypress.spec.relative.replace('cypress/e2e/', '')}/${runnable.parent.title} -- ${test.title} (failed).png`
        addContext({ test }, screenshot)
    }
})
