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

// Mock global properties that are used in the Vue components
(window as any).scrollToMessage = () => {};

// Mock global constants directly on globalThis
(globalThis as any).__IS_DETACHED_MODE__ = false;
(globalThis as any).__IS_ONLINE_MODE__ = true;
(globalThis as any).__USE_FONTS_OVER_CDN__ = false;

// Mock the problematic @detached import that HtmlExporter.ts tries to load
import { vi } from "vitest";

vi.mock("@detached/index.html?raw", () => ({
  default:
    '<html lang="en"><head><title>Mock Detached HTML</title></head><body><div id="app"></div></body></html>',
}));

// Mock fetch globally
(globalThis as any).fetch =
  globalThis.fetch ||
  (() =>
    Promise.resolve({
      ok: true,
      json: () => Promise.resolve({}),
    }));

// Mock window methods
(window as any).open = () => null;
(window as any).alert = () => {};
