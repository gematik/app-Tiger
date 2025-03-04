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
import { inject, onMounted, ref } from "vue";
import "simple-syntax-highlighter/dist/sshpre.css";
import { useHtmlExporter } from "@/api/HtmlExporter.ts";
import { Modal } from "bootstrap";
import { rbelFilterSymbol } from "@/api/RbelFilter.ts";

const filter = inject(rbelFilterSymbol)!;

const { isLoading, download } = useHtmlExporter(filter.rbelPath, {});

const modal = ref<Modal | null>(null);
const exportWithFilterToggle = ref(false);

function getYYMMDDHHMMSS(date: Date): string {
  const yy = String(date.getFullYear()).slice(-2);
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");

  const hh = String(date.getHours()).padStart(2, "0");
  const min = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");

  return `${yy}${mm}${dd}-${hh}${min}${ss}`;
}

async function onDownload(what: "tgr" | "html") {
  const date = new Date();

  const filename = `tiger-report-${getYYMMDDHHMMSS(date)}`;
  await download(filename, exportWithFilterToggle.value, what);
  modal.value?.hide();
}

onMounted(() => {
  modal.value = new Modal("#exportModal");
});
</script>

<template>
  <div
    class="modal modal-sm fade"
    id="exportModal"
    data-bs-backdrop="static"
    data-bs-keyboard="false"
    tabindex="-1"
    aria-labelledby="exportModalLabel"
    aria-hidden="true"
  >
    <div class="modal-dialog modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header">
          <h1 class="modal-title fs-5 me-3" id="rawContentModalLabel">Export Proxy Log</h1>
          <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <div class="form-check">
            <input
              class="form-check-input"
              type="checkbox"
              v-model="exportWithFilterToggle"
              id="switchExportWithFilter"
            />
            <label class="form-check-label" for="switchExportWithFilter"
              >Apply filters for export</label
            >
          </div>
        </div>
        <div class="modal-footer">
          <span>Download</span>
          <div class="btn-group" role="group">
            <button
              type="button"
              class="btn btn-outline-primary"
              title="Download as Tiger Rbel Log"
              @click="onDownload('tgr')"
              :disabled="isLoading"
            >
              <span
                v-if="isLoading"
                class="spinner-border spinner-border-sm"
                role="status"
                aria-hidden="true"
              />
              *.tgr
            </button>
            <button
              type="button"
              class="btn btn-outline-primary"
              title="Download as HTML"
              @click="onDownload('html')"
              :disabled="isLoading"
            >
              <span
                v-if="isLoading"
                class="spinner-border spinner-border-sm"
                role="status"
                aria-hidden="true"
              />
              *.html
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
