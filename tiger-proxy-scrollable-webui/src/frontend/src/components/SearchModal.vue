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
import { computed, inject, onMounted, type Ref, ref } from "vue";
import { toastSymbol } from "../Toast.ts";
import { messageQueueSymbol } from "@/api/MessageQueue.ts";
import { controlledComputed, refWithControl } from "@vueuse/core";
import "simple-syntax-highlighter/dist/sshpre.css";
import MessageItem from "@/components/MessageItem.vue";
import { Modal } from "bootstrap";
import { useSearchMessages } from "@/api/SearchMessages.ts";
import { rbelFilterSymbol } from "@/api/RbelFilter.ts";

const messageQueue = inject(messageQueueSymbol)!;
const rbelFilter = inject(rbelFilterSymbol)!;
const toast = inject(toastSymbol)!;

const search = useSearchMessages(rbelFilter.rbelPath, {
  onError: (errMsg: string) => {
    toast.showToast(errMsg);
  },
});

const searchResult = search.searchResult;
const invalidIndexResult = ref<string | null>(null);
const indexResult = ref<number | null>(null);

const searchQuery = refWithControl<string>("", {
  onBeforeChange: (value) => {
    if (value.startsWith("#")) {
      search.resetSearch();
      const nr = parseInt(value.slice(1));
      if (
        nr > 0 &&
        messageQueue.messagesMeta.value.findIndex((msg) => msg.sequenceNumber + 1 === nr) >= 0
      ) {
        indexResult.value = nr;
        invalidIndexResult.value = null;
      } else {
        indexResult.value = null;
        invalidIndexResult.value = `${value} not found`;
      }
    } else if (value.length > 0) {
      invalidIndexResult.value = null;
      indexResult.value = null;
      search.search(value);
    } else {
      search.resetSearch();
      invalidIndexResult.value = null;
      indexResult.value = null;
    }
  },
});

const errorMessage = controlledComputed(
  [searchResult as Ref<unknown>, invalidIndexResult as Ref<unknown>],
  () => {
    if (searchResult.value?.errorMessage) {
      return searchResult.value.errorMessage;
    } else if (invalidIndexResult.value) {
      return invalidIndexResult.value;
    }
    return null;
  },
);

const results = controlledComputed(
  [searchResult as Ref<unknown>, indexResult as Ref<unknown>],
  () => {
    if (searchResult.value) {
      return searchResult.value.messages ?? [];
    } else if (indexResult.value) {
      const msg = messageQueue.messagesMeta.value.find(
        (msg) => msg.sequenceNumber + 1 === indexResult.value,
      );
      return msg ? [msg] : [];
    }
    return [];
  },
);

const modal = ref<Modal | null>(null);

function jumpToFirstAndDismiss() {
  const firstMessageUuid = results.value[0]?.uuid;
  if (firstMessageUuid) {
    jumpToMessageAndDismiss(firstMessageUuid);
  }
}

function jumpToMessageAndDismiss(messageUuid: string) {
  if (messageUuid) {
    messageQueue.scrollToMessage(messageUuid);
    modal.value?.hide();
  }
}

onMounted(() => {
  modal.value = new Modal("#searchModal");
});

const nrOfSearchMatches = computed(
  () => searchResult.value?.totalFiltered ?? results.value?.length,
);
</script>

<template>
  <div class="modal fade" id="searchModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog">
      <div class="modal-content border-0 shadow-lg">
        <div class="modal-body">
          <input
            type="search"
            placeholder="Type to search..."
            :class="['form-control', 'form-control-lg', errorMessage ? 'is-invalid' : '']"
            aria-label="Enter Rbel Path"
            v-model="searchQuery"
            @keyup.enter="() => jumpToFirstAndDismiss()"
          />
          <div class="invalid-feedback">{{ errorMessage }}</div>
          <div class="mt-1 text-muted mb-2" v-if="!errorMessage">
            Search for messages by sequence number (e.g. <code>#123</code>) or JEXL Expression.
            Press <kbd>Enter &#9166;</kbd> to jump to the first result.
          </div>
          <div class="d-flex gap-2 align-items-center">
            <div>
              {{ nrOfSearchMatches }}
              {{
                Number(nrOfSearchMatches) == 0 ||
                Number(nrOfSearchMatches) > 1 ||
                typeof nrOfSearchMatches === "string"
                  ? "matches"
                  : "match"
              }}
            </div>
            <div
              class="text-muted spinner-border spinner-border-sm"
              role="status"
              v-if="search.isLoading.value"
            >
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          <div class="list-group">
            <a
              v-for="message in results"
              class="list-group-item list-group-item-action"
              :key="message.uuid"
            >
              <MessageItem
                :message="message"
                :on-height-changed="() => {}"
                :on-click="() => jumpToMessageAndDismiss(message.uuid)"
              />
            </a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
