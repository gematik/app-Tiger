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
import { computed, inject, nextTick, ref, watch } from "vue";
import { toastSymbol } from "../Toast.ts";
import { type Message } from "@/api/MessageQueue.ts";
import { useRbelTestTreeMessage } from "@/api/RbelTestMessage.ts";
import { rbelQueryModalSymbol } from "../RbelQueryModal.ts";
import "simple-syntax-highlighter/dist/sshpre.css";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { faCircleCheck, faCircleExclamation } from "@fortawesome/free-solid-svg-icons";

const DEFAULT_RBEL_PATH = "$.body";

const rbelQuery = inject(rbelQueryModalSymbol)!;

const toast = inject(toastSymbol)!;

const rbelQueryPath = ref<string>(DEFAULT_RBEL_PATH);

const selectedMessage = computed(() => rbelQuery.selectedMessage.value ?? ({} as Message));
const rbelTest = useRbelTestTreeMessage(selectedMessage, rbelQueryPath, {
  onError: (errMsg: string) => {
    toast.showToast(errMsg);
  },
});
const rbelPathTestResult = rbelTest.rbelPathTestResult;

defineExpose({
  reset: () => {
    rbelTest.resetTestResult();
    rbelQueryPath.value = DEFAULT_RBEL_PATH;
  },
});

const pathElements = ref<HTMLElement[]>([]);

function copyPathToInputField(element: Element) {
  let text = element.textContent ?? "";
  let el = element.previousElementSibling;
  let marker = el!.textContent;

  function containsNonWordCharacters(value: string) {
    return value.match("\\W") != null;
  }

  if (containsNonWordCharacters(text)) {
    text = "['" + text + "']";
  }

  while (el) {
    if (el.classList) {
      if (el.classList.contains("jexlResponseLink")) {
        const prevEl = el.previousElementSibling;
        if (
          prevEl &&
          prevEl.classList.contains("text-danger") &&
          (prevEl.textContent?.length ?? 0) < (marker?.length ?? 0)
        ) {
          if (containsNonWordCharacters(el.textContent ?? "")) {
            text = "['" + el.textContent + "']." + text;
          } else {
            text = el.textContent + "." + text;
          }
          marker = prevEl.textContent;
        }
      }
    }
    el = el.previousElementSibling;
  }

  const prefix = text.startsWith(".") ? "$" : "$.";
  rbelQueryPath.value = prefix + text;
}

watch(pathElements.value, async () => {
  if (pathElements.value.length > 0) {
    // Wait for the inner html to be rendered
    await nextTick();

    pathElements.value.forEach((element) => {
      const jexlResponseLinks = element.getElementsByClassName("jexlResponseLink");
      for (const element of jexlResponseLinks) {
        element.addEventListener("click", () => copyPathToInputField(element));
      }
    });
  }
});
</script>

<template>
  <div>
    <label for="rbelTreeExpressionTextArea" class="form-label">Expression</label>
    <textarea
      :class="['form-control', rbelPathTestResult?.errorMessage ? 'is-invalid' : '']"
      aria-label="Enter Rbel Path"
      v-model="rbelQueryPath"
      id="rbelTreeExpressionTextArea"
      placeholder="e.g. $.body"
      @keydown.enter.prevent="rbelTest.testRbelPathQuery"
    ></textarea>
    <div class="invalid-feedback">{{ rbelPathTestResult?.errorMessage }}</div>
    <div class="d-flex flex-row align-items-end justify-content-end gap-2 mt-4">
      <button class="btn btn-primary" type="button" @click="rbelTest.testRbelPathQuery">
        Test Expression
      </button>
    </div>
    <div
      class="card mt-3"
      v-if="rbelPathTestResult?.elementsWithTree && rbelPathTestResult?.elementsWithTree.length > 0"
    >
      <div
        class="d-flex flex-row align-items-center gap-2 border-start border-success border-4 rounded p-2 py-3"
      >
        <FontAwesomeIcon :icon="faCircleCheck" class="text-success fs-5" />
        <div>
          Matching elements for the expression <code>'{{ rbelPathTestResult?.query }}'</code>.
        </div>
      </div>
    </div>
    <div
      class="card mt-3"
      v-if="
        rbelPathTestResult &&
        (!rbelPathTestResult?.elementsWithTree ||
          rbelPathTestResult?.elementsWithTree?.length === 0)
      "
    >
      <div
        class="d-flex flex-row align-items-center gap-2 border-start border-warning border-4 rounded p-2 py-3"
      >
        <FontAwesomeIcon :icon="faCircleExclamation" class="text-warning fs-5" />
        <div>
          No matching elements for the expression <code>'{{ rbelPathTestResult?.query }}'</code>.
        </div>
      </div>
    </div>

    <div v-if="rbelPathTestResult?.elementsWithTree" class="d-flex flex-column">
      <div v-for="entry in rbelPathTestResult.elementsWithTree" :key="Object.keys(entry)[0]">
        <div class="ssh-pre">
          <pre class="ssh-pre__content" ref="pathElements" v-html="Object.values(entry)[0]" />
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss">
.jexlResponseLink {
  cursor: pointer;
  border-radius: 4px;
  padding: 1px 2px;

  &:hover {
    background: var(--gem-neutral-600);
  }
}
</style>
