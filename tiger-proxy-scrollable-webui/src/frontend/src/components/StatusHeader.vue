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
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { rbelFilterSymbol } from "@/api/RbelFilter.ts";
import { inject } from "vue";
import { faFilter, faSearch } from "@fortawesome/free-solid-svg-icons";

const rbelFilter = inject(rbelFilterSymbol)!;
</script>

<template>
  <div class="p-2 border-bottom d-flex gap-2 bg-white">
    <div class="filter input-group w-auto" v-if="__IS_ONLINE_MODE__">
      <span class="input-group-text">
        <FontAwesomeIcon :icon="faFilter" />
      </span>
      <input
        readonly
        data-bs-toggle="modal"
        data-bs-target="#filterBackdrop"
        aria-expanded="false"
        v-model="rbelFilter.rbelPath.value"
        type="text"
        class="form-control no-focus"
        placeholder="Rbel Path..."
      />
      <button
        class="btn btn-outline-secondary"
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
      class="btn btn-outline-primary"
      data-bs-toggle="modal"
      data-bs-target="#searchModal"
    >
      <FontAwesomeIcon :icon="faSearch" />&nbsp;Search
    </button>
  </div>
</template>

<style scoped lang="scss">
.sticky-header {
  background: var(--gem-neutral-050);
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
  min-width: 500px;
  text-overflow: ellipsis;
}

.no-focus:focus {
  outline: none !important;
  box-shadow: none !important;
  border-color: inherit !important;
}
</style>
