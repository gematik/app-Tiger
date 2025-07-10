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
            class="btn btn-outline-primary"
            @click="toggleVisibility"
            title="Toggle stack trace visibility"
          >
            <i :class="visible ? 'fas fa-minus' : 'fas fa-plus'" />
          </button>
          <div v-html="'FAILED: ' + replaceNewlines(message)" />
        </div>
      </td>
    </tr>
    <tr v-show="visible && stacktrace.length > 0">
      <td>
        <div class="font-monospace" v-html="replaceNewlines(stacktrace)" />
      </td>
    </tr>
  </table>
</template>

<script setup lang="ts">
import { ref } from "vue";

defineProps<{
  message: string;
  stacktrace: string;
}>();

const visible = ref(false);

function toggleVisibility() {
  visible.value = !visible.value;
}

function replaceNewlines(text: string): string {
  return text.replace(/\n/g, "<br>");
}
</script>

<style scoped>
.failure-text {
  color: var(--gem-error-700);
  background: var(--gem-error-100);
  margin-right: 0.5rem;
}

button.btn.btn-outline-primary {
  padding: 0.3rem 0.4rem; /* Adjust padding */
  font-size: 0.5rem; /* Match text size */
  border-radius: 0.2rem; /* Optional: reduce border radius */
  margin-right: 0.5rem; /* Add spacing between button and text */
}

.failure-message-container {
  display: flex;
  align-items: center; /* Align items vertically */
}
</style>
