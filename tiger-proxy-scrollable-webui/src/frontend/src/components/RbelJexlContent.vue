<!--

    Copyright 2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<script setup lang="ts">
import { computed, inject, ref } from "vue";
import { toastSymbol } from "../Toast.ts";
import { type Message } from "@/api/MessageQueue.ts";
import { useRbelTestMessage } from "@/api/RbelTestMessage.ts";
import { rbelQueryModalSymbol } from "../RbelQueryModal.ts";
import SshPre from "simple-syntax-highlighter";
import "simple-syntax-highlighter/dist/sshpre.css";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { rbelFilterSymbol } from "@/api/RbelFilter.ts";
import { faCircleCheck, faCircleExclamation } from "@fortawesome/free-solid-svg-icons";

const DEFAULT_JEXL_PATH = '"RbelHttpMessageFacet" =~facets';

const rbelFilter = inject(rbelFilterSymbol)!;
const rbelQuery = inject(rbelQueryModalSymbol)!;
const toast = inject(toastSymbol)!;

const rbelQueryPath = ref<string>(DEFAULT_JEXL_PATH);

const selectedMessage = computed(() => rbelQuery.selectedMessage.value ?? ({} as Message));
const rbelTest = useRbelTestMessage(selectedMessage, rbelQueryPath, {
  onError: (errMsg: string) => {
    toast.showToast(errMsg);
  },
});
const rbelPathTestResult = rbelTest.rbelPathTestResult;

defineExpose({
  reset: () => {
    rbelTest.resetTestResult();
    rbelQueryPath.value = DEFAULT_JEXL_PATH;
  },
});
</script>

<template>
  <div>
    <label for="rbelExpressionTextArea" class="form-label">Expression</label>
    <textarea
      :class="['form-control', rbelPathTestResult?.errorMessage ? 'is-invalid' : '']"
      aria-label="Enter Rbel Path"
      v-model="rbelQueryPath"
      id="rbelExpressionTextArea"
      placeholder='e.g. "RbelHttpMessageFacet" =~facets'
      @keydown.enter.prevent="rbelTest.testRbelPathQuery"
    ></textarea>
    <div class="invalid-feedback">{{ rbelPathTestResult?.errorMessage }}</div>
    <div class="d-flex flex-row align-items-end justify-content-end gap-2 mt-4">
      <button class="btn btn-primary" type="button" @click="rbelTest.testRbelPathQuery">
        Test Expression
      </button>
      <button
        class="btn btn-outline-primary"
        type="button"
        :disabled="
          !(
            rbelPathTestResult?.matchSuccessful === true &&
            rbelPathTestResult?.query === rbelQueryPath
          )
        "
        @click="() => (rbelFilter.rbelPath.value = rbelQueryPath)"
      >
        Apply as Filter
      </button>
    </div>
    <div class="card mt-3" v-if="rbelPathTestResult?.matchSuccessful === true">
      <div
        class="d-flex flex-row align-items-center gap-2 border-start border-success border-4 rounded p-2 py-3"
      >
        <FontAwesomeIcon :icon="faCircleCheck" class="text-success fs-5" />
        <div>
          Expression <code>'{{ rbelPathTestResult?.query }}'</code> matches! Below is the entire
          message context.
        </div>
      </div>
    </div>
    <div class="card mt-3" v-if="rbelPathTestResult?.matchSuccessful === false">
      <div
        class="d-flex flex-row align-items-center gap-2 border-start border-warning border-4 rounded p-2 py-3"
      >
        <FontAwesomeIcon :icon="faCircleExclamation" class="text-warning fs-5" />
        <div>
          Expression <code>'{{ rbelPathTestResult?.query }}'</code> doesn't match anything in the
          message. Below is the entire message context.
        </div>
      </div>
    </div>
    <div v-if="rbelPathTestResult?.messageContext">
      <SshPre
        v-for="[key, value] in Object.entries(rbelPathTestResult?.messageContext)"
        :key="key"
        language="json"
      >
        {{ JSON.stringify({ [key]: value }, undefined, 2).trim() }}
      </SshPre>
    </div>
  </div>
</template>

<style scoped></style>
