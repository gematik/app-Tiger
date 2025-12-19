<!--
  - Copyright 2021-2025 gematik GmbH
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
  - *******
  -
  - For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
  -
  -->

<script setup lang="ts">
import SplitButton from "primevue/splitbutton";
import Button from "primevue/button";
import { computed, ref } from "vue";
import PrefixedTagSelectionButton from "@/components/testselector/PrefixedTagSelectionButton.vue";

const props = defineProps<{
  tags: Set<string>;
}>();

const emit = defineEmits<{
  (e: "selectTag", tag: string): void;
  (e: "removeTag", tag: string): void;
  (e: "replaceSelection", tag: string): void;
}>();

const collapsed = ref(false);

const tagsGrouped = computed(() => {
  const map: Record<string, string[]> = {};
  props.tags.forEach((tag) => {
    const [prefix] = tag.split(":", 1);
    if (!prefix) return;
    if (!map[prefix]) {
      map[prefix] = [];
    }
    map[prefix].push(tag);
  });
  //sorting to keep prefixed tags together and single tags alphabetically
  return Object.entries(map).sort((a, b) => {
    if (a[1].length > 1 && b[1].length == 1) {
      return -1;
    } else if (a[1].length == 1 && b[1].length > 1) {
      return 1;
    } else {
      return a[0].localeCompare(b[0]);
    }
  });
});

const createTagActions = (tag: string) => [
  {
    label: "Unselect",
    icon: "fa fa-minus",
    command: () => {
      emit("removeTag", tag);
    },
  },
  {
    label: "Replace selection",
    icon: "fa fa-rotate-right",
    command: () => {
      emit("replaceSelection", tag);
    },
  },
];
</script>

<template>
  <div
    v-if="tags.size > 0"
    id="testselector-tags"
    class="collapsible d-inline-flex flex-wrap align-items-center"
  >
    <Button
      variant="text"
      severity="secondary"
      label="Tags"
      class="collapsible-toggle"
      @click="collapsed = !collapsed"
      :icon="collapsed ? 'fa fa-chevron-right' : 'fa fa-chevron-down'"
    >
    </Button>

    <template v-if="!collapsed">
      <template v-for="[prefix, tags] in tagsGrouped">
        <PrefixedTagSelectionButton
          v-if="tags.length > 1"
          :key="`${prefix}-multi`"
          :prefix="prefix"
          :tags="tags"
          @select-tag="(tag) => emit('selectTag', tag)"
          @remove-tag="(tag) => emit('removeTag', tag)"
          @replace-selection="(tag) => emit('replaceSelection', tag)"
        >
        </PrefixedTagSelectionButton>
        <SplitButton
          v-else-if="tags.length == 1"
          class="tag-selection-button"
          :key="`${prefix}-single`"
          :label="tags[0]"
          size="small"
          icon="fa fa-plus"
          @click="emit('selectTag', tags[0]!)"
          :model="createTagActions(tags[0]!)"
          rounded
        >
        </SplitButton>
      </template>
    </template>
  </div>
</template>

<style scoped>
/*Following styles are to ensure the tag buttons are small.
It is tricky because we have mix of styles from primevue with bootstrap (bootstrap styles the
button elements even if they have no specific bootstrap class.
*/
/* Scoped version (Vue 3): */
:deep(#testselector-tags .p-panel-header) {
  padding: 0.5rem 1rem; /* Reduce from default ~1rem 1.25rem */
  font-size: 0.875rem; /* Optional: make text smaller */
}

:deep(#testselector-tags .p-panel-title) {
  font-size: 0.875rem; /* Optional: make title text smaller */
}

:deep(.tag-selection-button button) {
  font-size: 0.875rem; /* small font size */
  padding: 0.25rem 0.5rem;
}

:deep(div.tag-selection-button) {
  margin-right: 0.25rem;
  margin-bottom: 0.25rem;
}

:deep(button.tag-selection-button) {
  font-size: 0.875rem;
  padding: 0.25rem 0.5rem;
  margin-right: 0.25rem;
  margin-bottom: 0.25rem;
}
</style>
