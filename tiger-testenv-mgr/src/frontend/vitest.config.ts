///
///
/// Copyright 2021-2025 gematik GmbH
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
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

import { defineConfig } from "vitest/config";
import viteConfig from "./vite.config";

// The vite.config exports a config factory (function). We must execute it to retain plugins (Vue)
// for Vitest; previously spreading the function dropped its properties so @vitejs/plugin-vue
// never ran, causing .vue parsing failure.
export default defineConfig(async () => {
  const base =
    typeof viteConfig === "function" ? await viteConfig() : viteConfig;
  return {
    ...base,
    test: {
      // keep/override test-specific settings here; don't lose any future base.test if added
      ...(base as any).test,
      environment: "jsdom",
      globals: true,
      deps: {
        inline: ["@vitejs/plugin-vue"],
      },
    },
  };
});
