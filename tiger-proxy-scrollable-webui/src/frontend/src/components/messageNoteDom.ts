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

export type JsonNoteEntry = {
  text: string;
  style: string;
};

export type ExtractedJsonNote = {
  placeholderText: string;
  notes: JsonNoteEntry[];
};

export function createJsonNotePlaceholderText(token: string) {
  return `"${token}"`;
}

export function extractJsonNotes(
  preEl: HTMLElement,
  createToken: () => string,
): ExtractedJsonNote[] {
  const extractedNotes: ExtractedJsonNote[] = [];
  preEl.querySelectorAll<HTMLElement>(".json-note-data").forEach((noteEl) => {
    const token = createToken();
    const placeholderText = createJsonNotePlaceholderText(token);
    try {
      const notes = JSON.parse(noteEl.dataset.notes ?? "[]") as JsonNoteEntry[];
      extractedNotes.push({ placeholderText, notes });
      noteEl.replaceWith(document.createTextNode(placeholderText));
    } catch {
      // Leave malformed note payload untouched.
    }
  });
  return extractedNotes;
}

export function findRangeInSingleTextNode(rootEl: HTMLElement, searchText: string): Range | null {
  const walker = document.createTreeWalker(rootEl, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const value = node.nodeValue ?? "";
    const start = value.indexOf(searchText);
    if (start === -1) {
      continue;
    }

    const range = document.createRange();
    range.setStart(node, start);
    range.setEnd(node, start + searchText.length);
    return range;
  }
  return null;
}

export function restoreJsonNotes(preEl: HTMLElement, extractedNotes: ExtractedJsonNote[]) {
  extractedNotes.forEach(({ placeholderText, notes }) => {
    const tokenRange = findRangeInSingleTextNode(preEl, placeholderText);
    if (!tokenRange) {
      return;
    }

    const wrapper = document.createElement("span");
    wrapper.className = "json-note";
    notes.forEach((note) => {
      const noteSpan = document.createElement("span");
      noteSpan.className = `rbel-postit rbel-postit__line ${note.style}`.trim();
      const em = document.createElement("i");
      em.textContent = note.text;
      noteSpan.appendChild(em);
      wrapper.appendChild(noteSpan);
    });

    tokenRange.deleteContents();
    tokenRange.insertNode(wrapper);
  });
}
