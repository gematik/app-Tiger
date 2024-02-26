/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

module.exports = {
    coverageDirectory: 'coverage',
    moduleFileExtensions: [
        'js',
        'ts',
        'tsx',
    ],
    rootDir: './',
    modulePaths: ['<rootDir>'],
    testEnvironment: 'node',
    testMatch: [
        '**/src/**/__tests__/*.+(ts|tsx)',
    ],
    testPathIgnorePatterns: ['/node_modules/'],
    transform: {
        '^.+\\.(ts|tsx)$': ['ts-jest', {
            tsconfig: 'tsconfig.json',
        }],
    },
    preset: 'ts-jest',
}
