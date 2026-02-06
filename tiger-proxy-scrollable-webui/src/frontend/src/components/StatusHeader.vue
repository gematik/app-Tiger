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
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { rbelFilterSymbol } from "@/api/RbelFilter.ts";
import { inject } from "vue";
import { faFilter, faSearch } from "@fortawesome/free-solid-svg-icons";

const rbelFilter = inject(rbelFilterSymbol)!;

function knownTigerVersion(tigerVersion: { version: string; buildDate: string }): boolean {
  return tigerVersion != null && !tigerVersion.version.includes("unknown");
}

defineProps<{
  tigerVersion: {
    version: string;
    buildDate: string;
  } | void;
}>();
</script>

<template>
  <div class="p-2 border-bottom d-flex gap-2 text-nowrap">
    <div class="filter input-group" v-if="__IS_ONLINE_MODE__">
      <span class="input-group-text">
        <FontAwesomeIcon :icon="faFilter" />
      </span>
      <input
        readonly
        id="test-rbel-path-input"
        data-bs-toggle="modal"
        data-bs-target="#filterBackdrop"
        aria-expanded="false"
        v-model="rbelFilter.rbelPath.value"
        type="text"
        class="form-control no-focus test-input-filter"
        placeholder="Rbel Path..."
      />
      <button
        class="btn btn-outline-secondary test-btn-reset-filter"
        id="test-reset-filter-button"
        type="button"
        @click="() => (rbelFilter.rbelPath.value = '')"
        :disabled="!rbelFilter.rbelPath.value"
      >
        Reset Filter
      </button>
    </div>
    <!-- Search Button -->
    <button
      type="button"
      class="btn btn-outline-primary test-btn-search"
      data-bs-toggle="modal"
      data-bs-target="#searchModal"
    >
      <FontAwesomeIcon :icon="faSearch" />&nbsp;Search
    </button>
    <div
      v-if="tigerVersion && knownTigerVersion(tigerVersion)"
      class="tiger-version align-self-center text-start lh-1 ms-auto font-monospace"
    >
      <p>
        {{ tigerVersion.version }}<br /><em>Build date: {{ tigerVersion.buildDate }}</em>
      </p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.sticky-header {
  background: var(--gem-neutral-050);
}

.tiger-version {
  font-size: x-small;
}

.logo {
  font-family: noto-sans, sans-serif;
  font-weight: 600;
  font-size: 22px;
  line-height: 1.5em;
  color: var(--gem-primary-700);
}

.logo::before {
  content: "";
  display: inline-block;
  background: url("../assets/tiger-mono-bg.svg") no-repeat center center;
  background-size: contain;
  width: 1.7em;
  height: 1.7em;
  vertical-align: middle;
  margin-right: 12px;
  border-radius: 4px;
}

.filter {
  min-width: 300px;
  width: 100%;
  max-width: 500px;
  text-overflow: ellipsis;
}

.no-focus:focus {
  outline: none !important;
  box-shadow: none !important;
  border-color: inherit !important;
}
</style>
