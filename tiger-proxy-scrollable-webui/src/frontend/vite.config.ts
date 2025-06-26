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

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "path";
import svgLoader from "vite-svg-loader";
import { fileURLToPath, URL } from "node:url";
import { viteSingleFile } from "vite-plugin-singlefile";

// https://vite.dev/config/
export default defineConfig(({mode}) => {
  const isDetachedDistMode = mode === "detached";

  return {
    resolve: {
      alias: {
        "~fontawesome": path.resolve(__dirname, "node_modules/@fortawesome/fontawesome-free"),
        "@detached": path.resolve(__dirname, "dist-detached"),
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      proxy: {
        "/webui": {
          target: "http://localhost:8080",
          changeOrigin: true,
          rewrite: (path: string) => path.replace(/^\/webui/, "/webui"),
        },
      },
    },
    define: {
      __IS_DETACHED_MODE__: isDetachedDistMode,
      __IS_ONLINE_MODE__: !isDetachedDistMode,
      __USE_FONTS_OVER_CDN__: isDetachedDistMode,
    },
    plugins: [vue(), svgLoader(), isDetachedDistMode ? viteSingleFile() : null],
    build: {
      minify: true,
      outDir: isDetachedDistMode ? "dist-detached" : "dist",
      target: "ES2022",
      sourcemap: true,
    },
  };
});
