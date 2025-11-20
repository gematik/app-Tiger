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

import { describe, expect, it } from "vitest";
import * as path from "path";
import { exec } from "child_process";
import { promisify } from "util";
import { chromium } from "playwright";

const execAsync = promisify(exec);

describe("Detached Mode Router - Regression Test for TGR-1942", () => {
  it("should render content when HTML is opened in browser", async () => {
    // Build the detached version
    const buildCommand = "npm run build:detached";
    const frontendDir = path.resolve(__dirname, "..");

    console.log("Building detached version...");
    await execAsync(buildCommand, { cwd: frontendDir });

    // Get the path to the exported HTML file
    const htmlPath = path.resolve(frontendDir, "dist-detached", "index.html");
    const htmlFileUrl = `file://${htmlPath.replace(/\\/g, "/")}`;

    console.log("Opening HTML in headless browser:", htmlFileUrl);

    // Launch a headless browser and open the HTML file
    const browser = await chromium.launch();
    const page = await browser.newPage();

    // Navigate to the HTML file
    await page.goto(htmlFileUrl);

    // Wait a bit for Vue to initialize and render
    await page.waitForTimeout(2000);

    // Get the content of the app div AFTER JavaScript has executed
    const appDivContent = await page.evaluate(() => {
      const appDiv = document.querySelector("#app");
      return appDiv ? appDiv.innerHTML : "";
    });

    console.log("App div content length after JS execution:", appDivContent.length);
    console.log("App div content preview:", appDivContent.substring(0, 300));

    await browser.close();

    // The app div should contain rendered content (not empty)
    expect(appDivContent.trim().length).toBeGreaterThan(100);

    // Verify there's actual Vue-rendered content
    expect(appDivContent).toContain("data-v-"); // Vue adds data-v-* attributes
  }, 120000); // 2 minute timeout for build and browser test
});
