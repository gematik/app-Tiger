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

<script setup lang="ts">

import {inject, onMounted, onUnmounted, Ref, ref} from "vue";
import "ag-grid-community/styles/ag-grid.css"
import "ag-grid-community/styles/ag-theme-alpine.css"
import TigerConfigurationPropertyDto from "@/types/TigerConfigurationPropertyDto";
import ConfigurationValueCell from "@/components/global_configuration/ConfigurationValueCell.vue";
import ConfigurationSourceCell from "@/components/global_configuration/ConfigurationSourceCell.vue"
import EditActionButtons from "@/components/global_configuration/EditActionButtons.vue";
import ConfigurationValueCellEditor from "@/components/global_configuration/ConfigurationValueCellEditor.vue";
import {Emitter} from "mitt";
import {useConfigurationLoader} from "@/components/global_configuration/ConfigurationLoader";
import {CellClickedEvent, ColDef, GridApi} from "ag-grid-community";
import {AgGridVue} from "ag-grid-vue3";

const {loadConfigurationProperties} = useConfigurationLoader();


const CONFIGURATION_EDITOR_URL = 'global_configuration';

const emitter: Emitter<any> = inject('emitter') as Emitter<any>;
const configurationProperties = ref(new Array<TigerConfigurationPropertyDto>());

const editorGrid: Ref<typeof AgGridVue | null> = ref(null);
const gridApi: Ref<GridApi | null | undefined> = ref(undefined);

onMounted(async () => {
  configurationProperties.value = await loadConfigurationProperties();
  if (editorGrid.value) {
    gridApi.value = editorGrid.value.api
  }
  emitter.on("cellValueSaved", onCellValueSaved);
})

onUnmounted(() => {
  emitter.off("cellValueSaved", onCellValueSaved)
})

async function onCellValueSaved(data: TigerConfigurationPropertyDto) {
  try {
    const response = await fetch(import.meta.env.BASE_URL + CONFIGURATION_EDITOR_URL,
        {
          method: "PUT",
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(data)
        }
    )
    if (response.ok) {
      configurationProperties.value = await loadConfigurationProperties();
    }
  } catch (error) {
    console.error("Error updating configuration entry " + error)
  }
}

function onCellClicked(params: CellClickedEvent) {
  if (isClickInActionsColumn(params)) {
    const action = (params.event?.target as HTMLElement).dataset.action
    if (action === 'delete') {
      deleteRow(params);
    } else if (action === 'edit') {
      startEdit(params);
    }
  }
}

function isClickInActionsColumn(params: CellClickedEvent) {
  return params.column['colId'] === 'action' && (params.event?.target as HTMLElement).dataset.action;
}

function onClearFilters() {
  gridApi.value?.setFilterModel(null);
}

async function deleteRow(params: CellClickedEvent) {
  try {
    const response = await fetch(import.meta.env.BASE_URL + "global_configuration",
        {
          method: "DELETE",
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(params.data)
        }
    )
    if (response.ok) {
      configurationProperties.value = await loadConfigurationProperties();
    }
  } catch (error) {
    console.error("Error deleting configuration entry " + error)
  }
}

async function startEdit(params: CellClickedEvent) {
  if (params.node.rowIndex !== null) {
    params.api.startEditingCell({
          rowIndex: params.node.rowIndex,
          colKey: 'value',
        }
    );
  }
}

const defaultColDef: ColDef = {
  sortable: true, filter: true, editable: false, resizable: true,
  icons: {
    menu: '<i class="fa fa-filter"/>',
  }
}

const columnDefs: ColDef[] = [
  {
    headerName: "Source",
    field: "source",
    flex: 1,
    cellRenderer: ConfigurationSourceCell,
    minWidth: 100,
  },
  {
    headerName: "Key",
    field: "key",
    flex: 6,
    cellEditorPopup: true,
    minWidth: 80
  },
  {
    headerName: "Value", field: "value",
    colId: 'value',
    cellRenderer: ConfigurationValueCell,
    cellEditorPopup: true,
    cellEditor: ConfigurationValueCellEditor,
    cellEditorPopupPosition: 'over',
    flex: 8,
    autoHeight: true,
    editable: true,
    minWidth: 90
  },
  {
    headerName: "Action",
    cellRenderer: EditActionButtons,
    colId: 'action',
    cellClass: "text-end",
    flex: 1,
    filter: false,
    minWidth: 83
  }

];


</script>

<template>
  <div class="container flex items-center">
    <div class="text-start py-1">
      <button type="button" id="test-tg-config-editor-btn-clear-filters" @click.prevent="onClearFilters">Clear filters
      </button>
    </div>
    <ag-grid-vue
        class="ag-theme-alpine editor-table"
        id="test-tg-config-editor-table"
        ref="editorGrid"
        :rowData="configurationProperties"
        :columnDefs="columnDefs"
        :defaultColDef="defaultColDef"
        suppressClickEdit="false"
        suppressNavigable="true"
        suppressMenuHide="true"
        cellClass="no-border"
        domLayout="autoHeight"
        @cell-clicked="onCellClicked"
    >
    </ag-grid-vue>
  </div>
</template>

<style>
.editor-table {
  width: 100%;
  height: 100%;
}

</style>
