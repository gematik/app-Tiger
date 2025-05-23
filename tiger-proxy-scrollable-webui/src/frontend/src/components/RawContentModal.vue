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
<script setup lang="ts">
import { computed, inject } from "vue";
import { type Message } from "@/api/MessageQueue.ts";
import SshPre from "simple-syntax-highlighter";
import "simple-syntax-highlighter/dist/sshpre.css";
import { rawContentModalSymbol } from "../RawContentModal.ts";
import { useClipboard } from "@vueuse/core";

const rawContent = inject(rawContentModalSymbol)!;

const selected = computed(
  () => rawContent.selected.value ?? { message: {} as Message, rawContent: "" },
);

const { copy, copied } = useClipboard();
</script>

<template>
  <div
    class="modal modal-lg fade"
    id="rawContentModal"
    data-bs-backdrop="static"
    data-bs-keyboard="false"
    tabindex="-1"
    aria-labelledby="rawContentModalLabel"
    aria-hidden="true"
  >
    <div class="modal-dialog modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header">
          <h1 class="modal-title fs-5 me-3" id="rawContentModalLabel">
            Raw Content #{{ selected.message.sequenceNumber + 1 }}
          </h1>
          <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <div class="position-relative">
            <SshPre>{{ selected.rawContent }}</SshPre>
            <button
              class="btn btn-sm btn-primary position-absolute top-0 end-0 m-2"
              @click="
                () => {
                  copied = true;
                  copy(selected.rawContent);
                }
              "
              title="Copy to clipboard"
            >
              <i class="fas fa-copy"></i>
              <span v-if="copied" class="check-icon ms-1">Copied!</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ssh-pre {
  margin-top: 0 !important;
}
</style>
