<!--
  - Copyright 2024 gematik GmbH
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
  -->

<script lang="ts">
// explicitly without "setup" because of issues with ag-grid. See https://stackoverflow.com/questions/73032489/ag-grid-framework-component-is-missing-the-method-getvalue-in-production-buil
import { inject, nextTick, onMounted, Ref, ref } from "vue";
import { ICellEditorParams } from "ag-grid-community";
import { Emitter } from "mitt";

export default {
  props: {
    params: Object as () => ICellEditorParams,
  },
  setup(props: any) {
    const emitter: Emitter<any> = inject("emitter") as Emitter<any>;
    const originalValue = ref(props.params.value);
    const editedValue = ref(originalValue.value);
    // Reference to the input element
    const textArea: Ref<HTMLInputElement | null> = ref(null);
    const columnWidth = ref(0);

    const getValue = () => {
      return originalValue.value;
    };

    // Gets called once before editing starts, to give editor a chance to
    // cancel the editing before it even starts.
    const isCancelBeforeStart = () => {
      return false;
    };

    // Gets called once when editing is finished (eg if Enter is pressed).
    // If you return true, then the result of the edit will be ignored.
    const isCancelAfterEnd = () => {
      return false;
    };

    const saveEditing = () => {
      if (originalValue.value !== editedValue.value) {
        //not really saving anything, just sending an event so that the TigerConfigurationEditor makes the actually saving
        emitter.emit("cellValueSaved", {
          ...props.params.data,
          value: editedValue.value,
        });
      }
      props.params.stopEditing();
    };

    const cancelEditing = () => {
      props.params.stopEditing(true); // true means we are canceling the editing
    };

    nextTick(() => {
      // focus on the input field once editing starts
      if (textArea.value) {
        textArea.value.focus();
      }
    });

    onMounted(() => {
      const column = props.params.column;
      if (column) {
        columnWidth.value = column.getActualWidth();
      }
    });

    return {
      editedValue,
      columnWidth,
      getValue,
      isCancelBeforeStart,
      isCancelAfterEnd,
      saveEditing,
      cancelEditing,
    };
  },
};
</script>

<template>
  <div
    class="configuration_value_editor p-2 border-1 border-dark-subtle bmt-2 bg-white rounded"
    :style="{ width: columnWidth + 'px' }"
  >
    <textarea
      id="test-tg-config-editor-text-area"
      v-model="editedValue"
      class="form-control"
      style="resize: both"
      rows="3"
      @keydown.enter="saveEditing"
    ></textarea>
    <div class="mt-2 btn-group">
      <button class="btn btn-primary" @click="saveEditing">
        <i id="test-tg-config-editor-btn-save" class="fa fa-floppy-disk"></i>
        Save
      </button>
      <button class="btn btn-secondary" @click="cancelEditing">
        <i id="test-tg-config-editor-btn-cancel" class="fa fa-ban"></i> Cancel
      </button>
    </div>
  </div>
</template>
