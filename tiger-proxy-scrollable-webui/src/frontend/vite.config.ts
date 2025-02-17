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



import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path';
import svgLoader from 'vite-svg-loader';
import {fileURLToPath, URL} from "node:url";


// https://vite.dev/config/
export default defineConfig({
  resolve: {
    alias: {
      "~bootstrap": path.resolve(__dirname, "node_modules/bootstrap"),
      "~fontawesome": path.resolve(__dirname, "node_modules/@fortawesome/fontawesome-free"),
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    proxy: {
      "/api/route": {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/api/, "/"),
      },
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/api/, "/nextwebui"),
      },
    },
  },
  plugins: [vue(), svgLoader()],
  build: {
    sourcemap: true,
  },
});
