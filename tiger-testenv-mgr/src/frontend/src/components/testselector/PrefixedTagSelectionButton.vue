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
import { ref } from "vue";
import ButtonGroup from "primevue/buttongroup";
import Button from "primevue/button";
import Popover from "primevue/popover";
import Listbox from "primevue/listbox";

defineProps<{
  prefix: string;
  tags: string[];
}>();

const popoverMenu = ref();
function menuToggle(event: Event) {
  popoverMenu.value.toggle(event);
}

const emit = defineEmits<{
  (e: "selectTag", tag: string): void;
  (e: "removeTag", tag: string): void;
  (e: "replaceSelection", tag: string): void;
}>();
</script>

<template>
  <Button
    class="tag-selection-button"
    :label="prefix + '...'"
    size="small"
    rounded
    @click="menuToggle"
  ></Button>
  <Popover ref="popoverMenu">
    <Listbox :options="tags" filter listStyle="max-height:50vh">
      <template #option="slotProps">
        <ButtonGroup class="flex-fill d-flex">
          <Button
            icon="fa fa-plus"
            severity="secondary"
            variant="outlined"
            :label="slotProps.option"
            size="small"
            title="Add tag to selection"
            class="flex-grow-1 justify-content-start"
            @click="emit('selectTag', slotProps.option)"
          ></Button>
          <Button
            class="tag-alternative-action p-2"
            variant="outlined"
            severity="danger"
            icon="fa fa-minus"
            size="small"
            title="Remove tag from selection"
            @click="emit('removeTag', slotProps.option)"
          ></Button>
          <Button
            class="tag-alternative-action p-2"
            severity="info"
            variant="outlined"
            icon="fa fa-rotate-right"
            size="small"
            title="Replace selection with this tag"
            @click="emit('replaceSelection', slotProps.option)"
          ></Button>
        </ButtonGroup>
      </template>
    </Listbox>
  </Popover>
</template>

<style scoped>
:deep(.p-listbox-option) {
  padding-top: 5px;
  padding-bottom: 5px;
}
</style>
