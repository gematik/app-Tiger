<!--


    Copyright 2021-2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    *******

    For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.

-->
<template>
  <table v-if="message.length > 0" class="failure-text">
    <tr>
      <td>
        <div class="failure-message-container">
          <button
            v-if="stacktrace.length > 0"
            class="btn btn-outline-primary stacktrace-toggle-btn"
            @click="toggleStackTraceVisibility"
            title="Toggle stack trace visibility"
          >
            <i :class="stackTraceVisible ? 'fas fa-minus' : 'fas fa-plus'" />
          </button>
          <div v-html="'FAILED: ' + replaceNewlines(message)" />
        </div>
      </td>
    </tr>
    <!-- Display mismatch notes as dropdown -->
    <tr v-if="mismatchNotes.length > 0">
      <td>
        <div class="mismatch-controls d-flex align-items-center">
          <button
            class="btn btn-outline-secondary btn-sm mismatch-nav-button"
            @click.prevent="prevMismatch"
            title="Previous mismatch note"
          >
            <i class="fas fa-chevron-up" />
          </button>
          <button
            class="btn btn-outline-secondary btn-sm mismatch-nav-button"
            @click.prevent="nextMismatch"
            title="Next mismatch note"
          >
            <i class="fas fa-chevron-down" />
          </button>
          <!-- PrimeVue dropdown for mismatch notes -->
          <Dropdown
            :options="mismatchDropdownOptions"
            optionLabel="text"
            optionValue="value"
            v-model="activeMismatchNoteIndex"
            class="mismatch-dropdown me-2"
            aria-label="Select mismatch note"
            data-testid="mismatch-dropdown"
            placeholder="Select mismatch"
          >
            <template #option="slotProps">
              <span class="badge rbelDetailsBadge">{{
                slotProps.option.sequenceNumber
              }}</span>
              <span class="ms-1">{{ slotProps.option.text }}</span>
            </template>
            <template #value>
              <span v-if="activeMismatchNoteIndex === -1" class="text-muted"
                >Select mismatch</span
              >
              <template v-else>
                <span class="badge rbelDetailsBadge">{{
                  mismatchDropdownOptions[activeMismatchNoteIndex]
                    ?.sequenceNumber
                }}</span>
                <span class="ms-1">{{
                  mismatchDropdownOptions[activeMismatchNoteIndex]?.text
                }}</span>
              </template>
            </template>
          </Dropdown>
        </div>
      </td>
    </tr>
    <tr v-show="stackTraceVisible && stacktrace.length > 0">
      <td>
        <div
          class="font-monospace stacktrace-container stacktrace-text"
          v-html="replaceNewlines(stacktrace)"
        />
      </td>
    </tr>
  </table>
</template>

<script setup lang="ts">
import { computed, defineProps, inject, ref, watch } from "vue";
import Dropdown from "primevue/dropdown";
import type { IMismatchNote } from "@/types/testsuite/MismatchNote";
import type MessageMetaDataDto from "@/types/rbel/MessageMetaDataDto";
import type { Emitter } from "mitt";

// define props
type Props = {
  message: string;
  stacktrace: string;
  mismatchNotes: IMismatchNote[];
  allRbelMetaData: MessageMetaDataDto[];
};
const props = defineProps<Props>();
const { message, stacktrace, mismatchNotes, allRbelMetaData } = props;
const emitter: Emitter<any> = inject("emitter") as Emitter<any>;
const stackTraceVisible = ref(false);
const activeMismatchNoteIndex = ref<number>(-1);
const mismatchDropdownOptions = computed(() =>
  mismatchNotes.map((note, i) => ({
    sequenceNumber: note.sequenceNumber + 1,
    value: i,
    text: note.value,
  })),
);

function toggleStackTraceVisibility() {
  stackTraceVisible.value = !stackTraceVisible.value;
}

function replaceNewlines(text: string | string[]): string {
  const str = Array.isArray(text) ? text.join("\n") : text;
  return str.replace(/\n/g, "<br>");
}

function navigateToMismatch() {
  const notes = mismatchNotes;
  if (
    notes.length === 0 ||
    activeMismatchNoteIndex.value < 0 ||
    activeMismatchNoteIndex.value >= notes.length
  )
    return;
  const note = notes[activeMismatchNoteIndex.value];
  if (note) {
    const meta = allRbelMetaData.find(
      (m: MessageMetaDataDto) => m.sequenceNumber === note.sequenceNumber,
    );
    if (meta) {
      emitter.emit("scrollToRbelLogMessage", meta.uuid);
    }
  }
}

function nextMismatch() {
  const len = mismatchNotes.length;
  if (len === 0) return;
  activeMismatchNoteIndex.value =
    activeMismatchNoteIndex.value >= len - 1
      ? 0
      : activeMismatchNoteIndex.value + 1;
}

function prevMismatch() {
  const len = mismatchNotes.length;
  if (len === 0) return;
  activeMismatchNoteIndex.value =
    activeMismatchNoteIndex.value <= 0
      ? len - 1
      : activeMismatchNoteIndex.value - 1;
}

watch(activeMismatchNoteIndex, (val) => {
  if (val !== -1) {
    navigateToMismatch();
  }
});
</script>

<style scoped>
.failure-text {
  width: 100%;
  border-collapse: collapse;
  table-layout: auto;
  background-color: var(--gem-error-100);
  border: 1px solid var(--gem-error-700);
  border-radius: 0.25rem;
  overflow: hidden;
  font-size: 0.875rem; /* unify font size across entire failure block */
}

.failure-message-container {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.5rem;
  color: var(--gem-error-700);
  min-width: fit-content;
  word-break: break-word;
}

.stacktrace-container {
  margin-top: 0.5rem;
  margin-left: 2rem;
}

.stacktrace-text {
  background-color: var(--gem-error-100);
  border: 1px solid var(--gem-error-700);
  border-radius: 0.25rem;
  padding: 0.75rem;
  font-size: 0.875rem;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 300px;
  overflow-y: auto;
  color: var(--gem-error-700);
}

.mismatch-controls {
  margin-left: 2rem;
  margin-top: 0.5rem;
}

.mismatch-nav-button {
  min-width: 1rem;
  flex-shrink: 0;
  border: none !important;
  background: transparent !important;
  padding: 0.125rem 0.25rem;
  font-size: 0.75rem;
  line-height: 1;
  color: #6c757d;
}

.mismatch-nav-button:hover {
  background: rgba(0, 0, 0, 0.05) !important;
  color: #495057;
}

.mismatch-nav-button:disabled {
  opacity: 0.5;
  color: #adb5bd !important;
}

.mismatch-nav-button + .mismatch-nav-button {
  margin-left: 0.125rem;
}

/* Ensure dropdown text matches failure message text size */
.mismatch-dropdown {
  font-size: inherit !important; /* inherit from .failure-text */
  color: var(--gem-error-700) !important;
  font-weight: normal !important;
  background-color: var(--gem-error-100) !important;
  border: 1px solid var(--gem-error-700) !important;

  --p-highlight-background: var(--gem-error-300, #fca5a5) !important;
  --p-highlight-color: var(--gem-error-700) !important;
  --p-focus-ring-color: var(--gem-error-400, #f87171) !important;
}

/* Force exact font size on dropdown root and all internal elements */
.mismatch-dropdown,
.mismatch-dropdown * {
  font-size: 0.875rem !important;
}

.mismatch-dropdown,
.mismatch-dropdown.p-dropdown {
  display: flex !important;
  align-items: center !important;
}

.mismatch-dropdown :deep(.p-dropdown-label),
.mismatch-dropdown :deep(.p-inputtext),
.mismatch-dropdown :deep(.p-dropdown-trigger),
.mismatch-dropdown :deep(.p-dropdown) {
  vertical-align: middle !important;
  display: flex !important;
  align-items: center !important;
}

/* Extra specificity against PrimeVue .p-component */
.mismatch-dropdown.p-dropdown.p-component {
  font-size: inherit !important;
}

.mismatch-dropdown :deep(.p-dropdown-label),
.mismatch-dropdown :deep(.p-inputtext) {
  background-color: var(--gem-error-100) !important;
  font-size: inherit !important;
  color: var(--gem-error-700) !important;
  font-weight: normal !important;
  line-height: 1rem !important;
  padding-top: 0.075rem !important;
  padding-bottom: 0.075rem !important;
  min-height: 1.2rem !important;
  height: 1.2rem !important;
}

.mismatch-dropdown.p-dropdown {
  min-height: 1.2rem !important;
  height: 1.2rem !important;
}

.mismatch-dropdown,
.mismatch-dropdown.p-dropdown,
.mismatch-dropdown :deep(.p-dropdown-label),
.mismatch-dropdown :deep(.p-inputtext),
.mismatch-dropdown :deep(.p-dropdown-trigger),
.mismatch-dropdown :deep(.p-dropdown) {
  min-height: 2rem !important;
  height: 2rem !important;
  font-size: 1.1rem !important;
  line-height: 1.8 !important;
  padding-top: 0.25rem !important;
  padding-bottom: 0.25rem !important;
  padding-left: 0.5rem !important;
  padding-right: 0.5rem !important;
  border-width: 1px !important;
}

.mismatch-dropdown :deep(.p-dropdown-panel),
.mismatch-dropdown :deep(.p-dropdown-items),
.mismatch-dropdown :deep(.p-dropdown-item) {
  min-height: 2rem !important;
  height: 2rem !important;
  font-size: 1.1rem !important;
  line-height: 1.8 !important;
  padding-top: 0.25rem !important;
  padding-bottom: 0.25rem !important;
}

.rbelDetailsBadge {
  font-size: 100%;
  margin-right: 0.5rem;
  margin-top: 0.25rem;
  margin-bottom: 0.25rem;
  padding: 0.5em 1rem;
  border: 1px solid lightgray;
  background-color: #ecfcfe; /* TODO coming from bulma we need --gem-info colors */
  color: #0a8694;
  text-decoration: none;
}

.stacktrace-toggle-btn {
  padding: 0.125rem 0.25rem !important;
  font-size: 0.75rem !important;
  line-height: 1 !important;
}

.stacktrace-toggle-btn i {
  font-size: 0.85em !important;
}
</style>
