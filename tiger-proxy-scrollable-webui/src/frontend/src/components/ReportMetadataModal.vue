<!--
  ~
  ~ Copyright 2021-2025 gematik GmbH
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  ~
  ~ *******
  ~
  ~ For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
  ~
-->
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { faClipboard } from "@fortawesome/free-solid-svg-icons";
import { useProxyController } from "@/api/ProxyController.ts";
import type { ReportMetadataDto } from "@/api/MessageTypes.ts";

const proxyController = useProxyController({});

const metadata = ref<ReportMetadataDto | null>(null);
const copied = ref(false);

const configurationEntries = computed(() => Object.entries(metadata.value?.configuration ?? {}));

function quoteIfNeeded(value: string): string {
  if (
    value === "" ||
    value !== value.trim() ||
    /^(true|false|null|yes|no|on|off|~|-?\d+(\.\d+)?)$/i.test(value) ||
    /[:#[\]{}&*!|>%@`"']/.test(value) ||
    value.startsWith("-") ||
    value.includes("\n")
  ) {
    return `"${value.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n")}"`;
  }
  return value;
}

const yaml = computed(() => {
  const data = metadata.value;
  if (!data) return "";
  const lines: string[] = [];
  if (data.tigerVersion) lines.push(`tigerVersion: ${quoteIfNeeded(data.tigerVersion)}`);
  const appendList = (key: string, values: string[]) => {
    if (!values?.length) return;
    lines.push(`${key}:`);
    values.forEach((value) => lines.push(`  - ${quoteIfNeeded(value)}`));
  };
  appendList("activeParsers", data.activeParsers);
  appendList("inactiveParsers", data.inactiveParsers);
  if (configurationEntries.value.length) {
    lines.push("configuration:");
    configurationEntries.value.forEach(([key, value]) =>
      lines.push(`  ${quoteIfNeeded(key)}: ${quoteIfNeeded(value)}`),
    );
  }
  return lines.join("\n");
});

async function copyAsYaml() {
  await navigator.clipboard.writeText(yaml.value);
  copied.value = true;
  setTimeout(() => (copied.value = false), 2000);
}

onMounted(async () => {
  metadata.value = (await proxyController.getReportMetadata({ suppressError: true })) ?? null;
});

defineExpose({ metadata });
</script>

<template>
  <div
    class="modal fade"
    id="reportMetadataModal"
    tabindex="-1"
    aria-labelledby="reportMetadataModalLabel"
    aria-hidden="true"
  >
    <div class="modal-dialog modal-lg modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header bg-dark text-white">
          <h1 class="modal-title fs-5 me-3" id="reportMetadataModalLabel">Report metadata</h1>
          <button
            type="button"
            class="btn btn-sm btn-outline-light me-2 test-report-metadata-copy"
            title="Copy as YAML"
            @click="copyAsYaml"
          >
            <FontAwesomeIcon :icon="faClipboard" />&nbsp;{{ copied ? "Copied" : "Copy as YAML" }}
          </button>
          <button
            type="button"
            class="btn-close btn-close-white"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <p v-if="!metadata" class="text-muted mb-0">
            This log was exported by a Tiger version that did not record any metadata.
          </p>
          <template v-else>
            <section class="mb-4" v-if="metadata.tigerVersion">
              <h6>Tiger version</h6>
              <div class="test-report-metadata-version">{{ metadata.tigerVersion }}</div>
            </section>
            <section class="mb-4" v-if="metadata.activeParsers?.length">
              <h6>Active parsers</h6>
              <span
                v-for="parser in metadata.activeParsers"
                :key="parser"
                class="badge text-bg-primary me-1 test-report-metadata-active-parser"
                >{{ parser }}</span
              >
            </section>
            <section class="mb-4" v-if="metadata.inactiveParsers?.length">
              <h6>Known, but inactive parsers</h6>
              <span
                v-for="parser in metadata.inactiveParsers"
                :key="parser"
                class="badge text-bg-secondary me-1 test-report-metadata-inactive-parser"
                >{{ parser }}</span
              >
            </section>
            <section v-if="configurationEntries.length">
              <h6>Tiger Proxy configuration</h6>
              <div class="table-responsive">
                <table class="table table-sm table-striped rbel-metadata-table">
                  <tbody>
                    <tr v-for="[key, value] in configurationEntries" :key="key">
                      <td class="rbel-metadata-key">{{ key }}</td>
                      <td>{{ value }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rbel-metadata-table .rbel-metadata-key {
  width: 40%;
  font-weight: 600;
  word-break: break-all;
}

.rbel-metadata-table td {
  word-break: break-all;
}
</style>
