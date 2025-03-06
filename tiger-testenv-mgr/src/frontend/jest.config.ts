///
///
/// Copyright 2025 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import type { JestConfigWithTsJest } from "ts-jest";

const config: JestConfigWithTsJest = {
  coverageDirectory: "coverage",
  moduleFileExtensions: ["js", "ts", "tsx"],
  rootDir: "./",
  modulePaths: ["<rootDir>"],
  testEnvironment: "node",
  testMatch: ["**/src/**/__tests__/*.+(ts|tsx)"],
  testPathIgnorePatterns: ["/node_modules/"],
  transform: {
    "^.+\\.(ts|tsx)$": [
      "ts-jest",
      {
        tsconfig: "tsconfig.node.json"
      }
    ]
  },
  moduleNameMapper: {
    "^@/(.*)$": "<rootDir>/src/$1"
  },
  extensionsToTreatAsEsm: [".ts"],
  preset: "ts-jest/presets/default-esm"
};

export default config;
