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
import { computed, inject, onMounted, ref } from "vue";
import { type Message } from "@/api/MessageQueue.ts";
import { rbelQueryModalSymbol } from "../RbelQueryModal.ts";
import "simple-syntax-highlighter/dist/sshpre.css";
import RbelTreeContent from "@/components/RbelTreeContent.vue";
import RbelJexlContent from "@/components/RbelJexlContent.vue";
import { settingsSymbol } from "../Settings.ts";

const RbelTreeTab = "RbelTreeTab";
const RbelJexlTab = "RbelJexlTab";

const rbelQuery = inject(rbelQueryModalSymbol)!;
const rbelHelp = inject(settingsSymbol)!;
const selectedMessage = computed(() => rbelQuery.selectedMessage.value ?? ({} as Message));

const selectedTab = ref(RbelTreeTab);
const treeContentRef = ref<typeof RbelTreeContent | null>(null);
const jexlContentRef = ref<typeof RbelJexlContent | null>(null);

const modalRef = ref<HTMLDivElement | null>(null);
onMounted(() => {
  if (modalRef.value) {
    modalRef.value.addEventListener("show.bs.modal", () => {
      treeContentRef.value?.reset();
      jexlContentRef.value?.reset();
      showHelpSection.value = !rbelHelp.hideRbelHelp.value;
    });
  }
});

const showHelpSection = ref(false);
function toggleHelpSection() {
  showHelpSection.value = !showHelpSection.value;
  rbelHelp.hideRbelHelp.value = !showHelpSection.value;
}
</script>

<template>
  <div
    ref="modalRef"
    class="modal modal-xl fade"
    id="jexlQueryModal"
    data-bs-backdrop="static"
    data-bs-keyboard="false"
    tabindex="-1"
    aria-labelledby="jexlQueryModalLabel"
    aria-hidden="true"
  >
    <div class="modal-dialog modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header">
          <h1 class="modal-title fs-5 me-5" id="jexlQueryModalLabel">
            Inspect #{{ selectedMessage.sequenceNumber + 1 }}
          </h1>
          <div class="btn-group" role="group" aria-label="Select tab to inspect">
            <input
              v-model="selectedTab"
              :value="RbelTreeTab"
              type="radio"
              class="btn-check"
              name="btnradio"
              id="btnradioRbelTree"
              autocomplete="off"
              checked
            />
            <label class="btn btn-outline-primary" for="btnradioRbelTree">RBel Tree</label>

            <input
              v-model="selectedTab"
              :value="RbelJexlTab"
              type="radio"
              class="btn-check"
              name="btnradio"
              id="btnradioJexlExpression"
              autocomplete="off"
            />
            <label class="btn btn-outline-primary" for="btnradioJexlExpression"
              >JEXL Expression</label
            >
          </div>
          <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body d-flex flex-row">
          <div
            class="pe-3"
            :class="{ 'w-100': !showHelpSection }"
            :style="{ flex: showHelpSection ? 5 : 1 }"
          >
            <RbelTreeContent v-if="selectedTab === RbelTreeTab" ref="treeContentRef" />
            <RbelJexlContent v-if="selectedTab === RbelJexlTab" ref="jexlContentRef" />
          </div>
          <div
            class="sticky-top ps-3 border-1 border-start f-caption"
            :style="{
              flex: showHelpSection ? 2 : 0,
              'min-width': showHelpSection ? '260px' : '48px',
            }"
          >
            <div v-if="selectedTab === RbelTreeTab">
              <div
                :class="{
                  'd-flex align-items-center justify-content-between': showHelpSection,
                  'text-center': !showHelpSection,
                }"
              >
                <span class="fw-semibold" v-if="showHelpSection">RBeL-Path Quick Help</span>
                <span class="fw-semibold" v-else>Help</span>
                <button
                  type="button"
                  :class="[
                    'fa-solid',
                    'toggle-icon',
                    'msg-toggle',
                    'has-text-link',
                    showHelpSection ? 'fa-toggle-on' : 'fa-toggle-off',
                  ]"
                  aria-label="Hilfe ein-/ausblenden"
                  @click="toggleHelpSection"
                  :style="{
                    'font-size': '1.3rem',
                    border: 'none',
                    background: 'transparent',
                    padding: '0',
                    cursor: 'pointer',
                    'margin-left': showHelpSection ? '1rem' : '0',
                    'margin-top': showHelpSection ? '0' : '0.5rem',
                  }"
                ></button>
              </div>
              <div v-if="showHelpSection">
                <p>
                  RBeL-Path is an expression language inspired by XPath and JSON-Path, enabling
                  quick traversal of captured RBeL-Traffic (RbelElement-tree). For detailed
                  documentation, visit
                  <a
                    href="https://gematik.github.io/app-Tiger/Tiger-User-Manual.html#_rbel_path_details"
                    target="_blank"
                    >this page</a
                  >.
                </p>
                <span class="fw-semibold">Examples:</span>
                <ul class="list-group">
                  <li class="list-group-item">
                    <code>$.body</code> <br />
                    <small>Accesses the body of the message.</small>
                  </li>
                  <li class="list-group-item">
                    <code>$..Action</code> <br />
                    <small
                      >Finds all nodes named <strong>Action</strong> across the entire tree.</small
                    >
                  </li>
                  <li class="list-group-item">
                    <code>$.body..Action</code> <br />
                    <small>Finds all nodes named <strong>Action</strong> within the body.</small>
                  </li>
                  <li class="list-group-item">
                    <code>$..[?(content =~ "UTF-.*")]</code> <br />
                    <small
                      >Matches nodes with text starting with <strong>UTF-</strong> using a
                      JEXL-Expression.</small
                    >
                  </li>
                </ul>
              </div>
            </div>
            <div v-if="selectedTab === RbelJexlTab">
              <div
                :class="{
                  'd-flex align-items-center justify-content-between': showHelpSection,
                  'text-center': !showHelpSection,
                }"
              >
                <span class="fw-semibold" v-if="showHelpSection">JEXL Quick Help</span>
                <span class="fw-semibold" v-else>Help</span>
                <button
                  type="button"
                  :class="[
                    'fa-solid',
                    'toggle-icon',
                    'msg-toggle',
                    'has-text-link',
                    showHelpSection ? 'fa-toggle-on' : 'fa-toggle-off',
                  ]"
                  aria-label="switch help on/off"
                  @click="toggleHelpSection"
                  :style="{
                    'font-size': '1.3rem',
                    border: 'none',
                    background: 'transparent',
                    padding: '0',
                    cursor: 'pointer',
                    'margin-left': showHelpSection ? '1rem' : '0',
                    'margin-top': showHelpSection ? '0' : '0.5rem',
                  }"
                ></button>
              </div>
              <div v-if="showHelpSection">
                <p>
                  JEXL syntax is a powerful expression language used for evaluating conditions and
                  extracting data. For detailed documentation, visit
                  <a
                    href="https://commons.apache.org/proper/commons-jexl/reference/syntax.html"
                    target="_blank"
                    >this page</a
                  >. In addition, you can use <strong>RbelPath</strong> expressions, which are
                  described
                  <a
                    href="https://gematik.github.io/app-Tiger/Tiger-User-Manual.html#_rbel_path_details"
                    target="_blank"
                    >here</a
                  >.
                </p>
                <span class="fw-semibold">Examples:</span>
                <ul class="list-group">
                  <li class="list-group-item">
                    <code>"RbelHttpMessageFacet" =~ facets</code> <br />
                    <small
                      >Checks if the message has the <strong>RbelHttpMessageFacet</strong> facet.
                      <code>facets</code> is an array containing all recognized facets.</small
                    >
                  </li>
                  <li class="list-group-item">
                    <code>isRequest</code> <br />
                    <small>Checks if the message is a request.</small>
                  </li>
                  <li class="list-group-item">
                    <code>$.body.recordId == "X12349035"</code> <br />
                    <small
                      >Checks for the <strong>recordId</strong> in a decrypted EPA-VAU
                      message.</small
                    >
                  </li>
                  <li class="list-group-item">
                    <code>$.header.Content-Type == "application/json"</code> <br />
                    <small>Checks if the message is a JSON message.</small>
                  </li>
                  <li class="list-group-item">
                    <code>charset =~ "UTF-.*"</code> <br />
                    <small>Checks the <strong>charset</strong> using a regex pattern.</small>
                  </li>
                  <li class="list-group-item">
                    <code>$.body.recordId == "Y243631459" && charset == "UTF-8"</code> <br />
                    <small
                      >Combines multiple conditions to check both <strong>recordId</strong> and
                      <strong>charset</strong>.</small
                    >
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
