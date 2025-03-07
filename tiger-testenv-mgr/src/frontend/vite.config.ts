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

import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import path from "path";
import envCompatible from "vite-plugin-env-compatible";

// https://vite.dev/config/
export default defineConfig(() => {
    return {
        resolve: {
            alias: [
                {
                    find: /^~/,
                    replacement: ''
                },
                {
                    find: '@',
                    replacement: path.resolve(__dirname, 'src')
                }
            ],
        },
        server: {
            proxy: {
                '/testEnv': {
                    target: 'http://localhost:54727',
                    ws: true,
                    rewrite: path => path
                },
                '/testLog': {
                    target: 'http://localhost:54727',
                    ws: true,
                    rewrite: path => path
                }
            }
        },
        plugins: [vue(), envCompatible()],
        build: {
            minify: true,
            target: "ES2022",
        },
    };
});
