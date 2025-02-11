/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const fs = require("fs");

const jseslint = require("@eslint/js");
const tseslint = require("typescript-eslint");
const eslintPluginVue = require("eslint-plugin-vue");
const eslintPluginPrettierRecommended = require("eslint-plugin-prettier/recommended");

const filesToIgnore = fs
  .readFileSync(".gitignore", "utf8")
  .split("\n")
  .filter((line) => !(line.startsWith("#") || line.trim().length === 0));

module.exports = tseslint.config(
  {
    ignores: [
        ...filesToIgnore
    ],
  },
  {
    extends: [
      jseslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...eslintPluginVue.configs["flat/recommended"],
      eslintPluginPrettierRecommended,
    ],
    ignores: [ "**/__tests__/**" ],
    files: ["**/*.{ts,vue}"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: {
        node: true,
      },
      parserOptions: {
        parser: tseslint.parser,
      },
    },
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-inferrable-types": "off",
      "vue/multi-word-component-names": "off",
    },
  },
  {
    extends: [
      jseslint.configs.recommended,
      ...tseslint.configs.recommended,
      eslintPluginPrettierRecommended,
    ],
    files: ["**/*.{ts}"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: {
        node: true,
      },
      parserOptions: {
        parser: tseslint.parser,
      },
    },
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-inferrable-types": "off",
      "@typescript-eslint/no-unused-expressions": "off",
    },
  },
);
