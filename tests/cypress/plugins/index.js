/// <reference types="cypress" />

/**
 * @type {Cypress.PluginConfig}
 */
module.exports = (on, config) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    require('@jahia/cypress/dist/plugins/registerPlugins').registerPlugins(on, config)
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    require('cypress-terminal-report/src/installLogsPrinter')(on, {
        printLogsToConsole: 'onFail',
        printLogsToFile: 'always',
        outputRoot: config.projectRoot + '/results/',
        specRoot: 'cypress/e2e',
        outputTarget: {
            'cypress-logs|txt': 'txt',
        },
        defaultTrimLength: 50000,
        commandTrimLength: 5000,
        routeTrimLength: 5000,
    })

    return config
}
