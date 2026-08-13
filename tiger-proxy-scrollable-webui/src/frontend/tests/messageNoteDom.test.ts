///
///
/// Copyright 2021-2026 gematik GmbH
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
import hljs from "highlight.js/lib/core";
import json from "highlight.js/lib/languages/json";
import {
  createJsonNotePlaceholderText,
  findRangeInSingleTextNode,
  restoreJsonNotes,
} from "../src/components/messageNoteDom";

hljs.registerLanguage("json", json);

describe("message note dom helpers", () => {
  it("highlight.js keeps the JSON string as one text node", () => {
    const code = document.createElement("code");
    code.className = "language-json";
    const placeholderText = createJsonNotePlaceholderText("RBEL_JSON_NOTE_123");
    code.textContent = `{"note":${placeholderText}}`;

    hljs.highlightElement(code);

    const stringEl = code.querySelector(".hljs-string");
    expect(stringEl).not.toBeNull();
    expect(stringEl?.childNodes.length).toBe(1);
    expect(stringEl?.firstChild?.nodeType).toBe(Node.TEXT_NODE);
    expect(stringEl?.textContent).toContain("RBEL_JSON_NOTE_123");
    expect(findRangeInSingleTextNode(code, "RBEL_JSON_NOTE_123")).not.toBeNull();
  });

  it("restores the note from the highlighted JSON string", () => {
    const pre = document.createElement("pre");
    const code = document.createElement("code");
    code.className = "language-json";
    const placeholderText = createJsonNotePlaceholderText("RBEL_JSON_NOTE_123");
    code.textContent = `{"note":${placeholderText}}`;
    pre.appendChild(code);

    hljs.highlightElement(code);
    restoreJsonNotes(pre, [
      {
        placeholderText,
        notes: [{ text: "hello", style: "has-text-warning" }],
      },
    ]);

    expect(pre.querySelector(".json-note")).not.toBeNull();
    expect(pre.textContent).toContain("hello");
  });
});
