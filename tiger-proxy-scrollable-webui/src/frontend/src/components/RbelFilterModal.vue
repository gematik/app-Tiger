<!--
  - Copyright 2024 gematik GmbH
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -
  -->

<script setup lang="ts">
import {inject, onMounted, ref} from "vue";
import {useRbelTestFilter} from "@/api/RbelTestFilter.ts";
import {toastSymbol} from "../Toast.ts";
import {messageQueueSymbol} from "@/api/MessageQueue.ts";
import {computedWithControl, refWithControl} from "@vueuse/core";
import {rbelFilterSymbol} from "@/api/RbelFilter.ts";

const rbelFilter = inject(rbelFilterSymbol)!;
const messageQueue = inject(messageQueueSymbol)!;
const toast = inject(toastSymbol)!;

const isRbelPathInvalid = ref(false);
const filter = useRbelTestFilter({
  onError: (errMsg: string) => {
    isRbelPathInvalid.value = true;
    toast.showToast(errMsg);
  },
});

const rbelPath = refWithControl<string>(rbelFilter.rbelPath.value, {
  onBeforeChange: (value) => {
    if (value) filter.testRbel(value);
  },
});

const isRbelTestLoading = filter.isLoading;
const rbelTestResult = filter.rbelTestResult;

const modalRef = ref<HTMLDivElement | null>(null);
onMounted(() => {
  if (modalRef.value) {
    modalRef.value.addEventListener("show.bs.modal", () => {
      rbelPath.value = rbelFilter.rbelPath.value;
      filter.testRbel(rbelPath.value);
    });
  }
});

const quickFilter = computedWithControl(messageQueue.messagesMeta, () => {
  const recipient: string[] = [];
  const sender: string[] = [];
  messageQueue.messagesMeta.value.forEach((message) => {
    if (message.recipient) recipient.push(message.recipient);
    if (message.sender) sender.push(message.sender);
  });
  return {
    recipient: mostNFrequent(recipient, 20).sort((a, b) => a.localeCompare(b)),
    sender: mostNFrequent(sender, 20).sort((a, b) => a.localeCompare(b)),
  };
});

/**
 * Returns the n most frequent elements in the list.
 */
function mostNFrequent(list: string[], n: number): string[] {
  const freqMap = new Map<string, number>();
  list.forEach((entry) => freqMap.set(entry, (freqMap.get(entry) || 0) + 1));

  return [...freqMap.entries()]
  .sort((a, b) => b[1] - a[1])
  .slice(0, n)
  .map(([entry]) => entry);
}

function appendSender(sender: string) {
  rbelPath.value += `$.sender == "${sender}"`;
}

function appendReceiver(receiver: string) {
  rbelPath.value += `$.receiver == "${receiver}"`;
}
</script>

<template>
  <div
      ref="modalRef"
      class="modal fade"
      id="filterBackdrop"
      data-bs-backdrop="static"
      data-bs-keyboard="false"
      tabindex="-1"
      aria-labelledby="filterBackdropLabel"
      aria-hidden="true"
  >
    <div class="modal-dialog">
      <div class="modal-content" id="">
        <div class="modal-header">
          <h1 class="modal-title fs-5" id="filterBackdropLabel">Filter</h1>
          <button
              type="button"
              class="btn-close"
              data-bs-dismiss="modal"
              aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <label for="rbelFilterExpressionTextArea" class="form-label">JEXL Expression</label>
          <textarea
              :class="['form-control', rbelTestResult?.errorMessage ? 'is-invalid' : '']"
              aria-label="Enter JEXL Expression"
              v-model="rbelPath"
              id="rbelFilterExpressionTextArea"
          ></textarea>
          <div class="invalid-feedback">{{ rbelTestResult?.errorMessage }}</div>
          <div class="d-flex flex-row gap-2 mt-2">
            <div class="dropdown">
              <button
                  class="btn btn-outline-dark dropdown-toggle btn-sm"
                  type="button"
                  data-bs-toggle="dropdown"
                  aria-expanded="false"
              >
                Insert Recipient
              </button>
              <ul class="dropdown-menu test-select-recipient">
                <li v-for="recipient in quickFilter.recipient" :key="recipient">
                  <a class="dropdown-item" @click="() => appendReceiver(recipient)">
                    {{ recipient }}
                  </a>
                </li>
              </ul>
            </div>
            <div class="dropdown">
              <button
                  class="btn btn-outline-dark dropdown-toggle btn-sm"
                  type="button"
                  data-bs-toggle="dropdown"
                  aria-expanded="false"
              >
                Insert Sender
              </button>
              <ul class="dropdown-menu test-select-sender">
                <li v-for="sender in quickFilter.sender" :key="sender">
                  <a class="dropdown-item" @click="() => appendSender(sender)">{{ sender }}</a>
                </li>
              </ul>
            </div>
          </div>
          <div class="mt-4 f-caption">
            <span class="fw-semibold">RBeL-Path Quick Help</span>
            <div>
              <p>
                RBeL-Path is a powerful expression language inspired by XPath and JSON-Path,
                designed for quick navigation of captured RBeL-Traffic (RbelElement-tree). For
                detailed documentation, visit
                <a
                    href="https://gematik.github.io/app-Tiger/Tiger-User-Manual.html#_understanding_rbelpath"
                    target="_blank"
                >this page</a
                >.
              </p>
              <span class="fw-semibold">Examples:</span>
              <ul class="list-group">
                <li class="list-group-item">
                  <code>$.body</code> <br/>
                  <small>Accesses the body of the message.</small>
                </li>
                <li class="list-group-item">
                  <code>$..Action</code> <br/>
                  <small
                  >Finds all nodes named <strong>Action</strong> across the entire tree.</small
                  >
                </li>
                <li class="list-group-item">
                  <code>$.body..Action</code> <br/>
                  <small>Finds all nodes named <strong>Action</strong> within the body.</small>
                </li>
                <li class="list-group-item">
                  <code>$..[?(content =~ "UTF-.*")]</code> <br/>
                  <small
                  >Matches nodes with text starting with <strong>UTF-</strong> using a
                    JEXL-Expression.</small
                  >
                </li>
              </ul>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <div
              class="pe-2 d-inline-flex align-content-center gap-2"
              v-if="rbelTestResult || isRbelTestLoading"
          >
            <div
                class="text-muted spinner-border spinner-border-sm"
                role="status"
                v-if="isRbelTestLoading"
            >
              <span class="visually-hidden">Loading...</span>
            </div>
            <span v-if="rbelTestResult" class="f-caption" id="filteredMessage"
            >Matched {{ rbelTestResult.totalFiltered ?? "?" }} of {{ rbelTestResult.total }}</span
            >
          </div>
          <button
              type="button"
              class="btn btn-primary"
              id="setFilterCriterionBtn"
              :disabled="rbelTestResult?.errorMessage != null"
              data-bs-dismiss="modal"
              @click="() => (rbelFilter.rbelPath.value = rbelPath)"
          >
            Apply Filter
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
