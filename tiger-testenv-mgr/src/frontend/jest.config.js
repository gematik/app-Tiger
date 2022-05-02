/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

module.exports = {
  coverageDirectory: 'coverage',
  globals: {
    'ts-jest': {
      tsConfig: 'tsconfig.json',
    },
  },
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
    '^.+\\.(ts|tsx)$': 'ts-jest',
  },
  preset: 'ts-jest',
}
