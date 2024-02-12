<!--
  - Copyright (c) 2024 gematik GmbH
  - 
  - Licensed under the Apache License, Version 2.0 (the License);
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  - 
  -     http://www.apache.org/licenses/LICENSE-2.0
  - 
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an 'AS IS' BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="row">
    <code ref="elementWithValue" class="value col-11 gy-1 hljs" id="test-tg-config-editor-table-row"
          :class="{  'text-truncate': isOverflowed && !expanded, 'text-break': expanded, 'multi-line': expanded}"
          title="double click to edit"
          v-html="highlighted.value">
    </code>
    <div class="col-1 py-1" v-if="isOverflowed || expanded"><i v-on:click="clickExpandButton" class="fa-solid"
                                                               :class="{'fa-up-right-and-down-left-from-center': !expanded, 'fa-down-left-and-up-right-to-center': expanded }"></i>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ComputedRef, onMounted, onUnmounted, ref} from "vue";
import {ICellRendererParams} from "ag-grid-community";
import hljs from 'highlight.js/lib/core';
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import plaintext from 'highlight.js/lib/languages/plaintext'
import 'highlight.js/styles/stackoverflow-light.css';
import {AutoHighlightResult} from "highlight.js";

hljs.registerLanguage("json", json)
hljs.registerLanguage("xml", xml)
hljs.registerLanguage("html", xml)
hljs.registerLanguage("plaintext", plaintext)

const props = defineProps<{
  params: ICellRendererParams
}>()

const elementWithValue = ref<HTMLDivElement | null>(null);
const isOverflowed = ref(false);
const expanded = ref(false);
const resizeObserver: ResizeObserver = new ResizeObserver((entries) => {
  for (const entry of entries) {
    isOverflowed.value = checkOverflow(entry.target)
  }
})

const highlighted: ComputedRef<AutoHighlightResult> = computed(() => {
  return hljs.highlightAuto(props.params.value)
})


onMounted(() => {
  if (elementWithValue.value) {
    resizeObserver.observe(elementWithValue.value)
  }
});

onUnmounted(() => {
  resizeObserver.disconnect()
});

function checkOverflow(element: Element): boolean {
  return element.scrollWidth > element.clientWidth;
}

function clickExpandButton() {
  expanded.value = !expanded.value;
}


</script>
<style>

#test-tg-config-editor-table-row {
  background: inherit;
  border: 2px solid #e0e0e0;
}

#test-tg-config-editor-table-row:hover {
  cursor: pointer;
}

.multi-line {
  white-space: normal;
}
</style>
