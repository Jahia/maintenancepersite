import { defineConfig } from 'cypress'
import * as fs from 'node:fs'

export default defineConfig({
    chromeWebSecurity: false,
    defaultCommandTimeout: 10000,
    requestTimeout: 60000,
    responseTimeout: 60000,
    reporter: 'cypress-multi-reporters',
    reporterOptions: {
        configFile: 'reporter-config.json',
    },
    screenshotsFolder: './results/screenshots',
    videosFolder: './results/videos',
    viewportWidth: 1366,
    viewportHeight: 768,
    watchForFileChanges: false,
    e2e: {
        specPattern: ['**/**.cy.ts'],
        setupNodeEvents(on, config) {
            on('task', {
                readFileMaybe(filename) {
                    if (fs.existsSync(filename)) {
                        return fs.readFileSync(filename, 'utf8')
                    }

                    return null
                },
            })

            // eslint-disable-next-line @typescript-eslint/no-require-imports
            return require('./cypress/plugins/index.js')(on, config)
        },
        excludeSpecPattern: ['**/*.ignore.ts'],
        baseUrl: 'http://localhost:8080',
    },
})
