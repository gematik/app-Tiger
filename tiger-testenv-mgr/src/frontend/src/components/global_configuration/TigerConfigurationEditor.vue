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
import { inject, onMounted, onUnmounted, type Ref, ref, shallowRef } from "vue";
import {
  type CellClickedEvent,
  CellStyleModule,
  ClientSideRowModelModule,
  type ColDef,
  CustomEditorModule,
  EventApiModule,
  type GridApi,
  type GridReadyEvent,
  RowAutoHeightModule,
  TextFilterModule,
  themeAlpine,
  ValidationModule,
} from "ag-grid-community";
import TigerConfigurationPropertyDto from "@/types/TigerConfigurationPropertyDto";
import ConfigurationValueCell from "@/components/global_configuration/ConfigurationValueCell.vue";
import ConfigurationSourceCell from "@/components/global_configuration/ConfigurationSourceCell.vue";
import EditActionButtons from "@/components/global_configuration/EditActionButtons.vue";
import ConfigurationValueCellEditor from "@/components/global_configuration/ConfigurationValueCellEditor.vue";
import { type Emitter } from "mitt";
import { useConfigurationLoader } from "@/components/global_configuration/ConfigurationLoader";
import { AgGridVue } from "ag-grid-vue3";

//By using modules, we just load what we need and can reduce bundle size.
//If additional functionality is needed, check https://www.ag-grid.com/javascript-data-grid/modules/#selecting-modules
const modules = [
  ValidationModule,
  ClientSideRowModelModule,
  CellStyleModule,
  CustomEditorModule,
  EventApiModule,
  RowAutoHeightModule,
  TextFilterModule,
];

const {
  loadConfigurationProperties,
  deleteConfigurationProperty,
  saveConfigurationProperty,
  importConfig,
} = useConfigurationLoader();

const emitter: Emitter<any> = inject("emitter") as Emitter<any>;
const configurationProperties = ref(new Array<TigerConfigurationPropertyDto>());

const editorGrid: Ref<typeof AgGridVue | null> = ref(null);
const gridApi: Ref<GridApi | null | undefined> = shallowRef(undefined);
const importFileStatus = ref("");

const isImportButtonDisabled = ref(false);
const isRefreshButtonDisabled = ref(false);

onMounted(async () => {
  configurationProperties.value = await loadConfigurationProperties();
  emitter.on("cellValueSaved", onCellValueSaved);
});

// Store the api for later use
const onGridReady = (params: GridReadyEvent) => {
  gridApi.value = params.api;
};

onUnmounted(() => {
  emitter.off("cellValueSaved", onCellValueSaved);
});

async function onCellValueSaved(data: TigerConfigurationPropertyDto) {
  const response = await saveConfigurationProperty(data);
  if (response.ok) {
    configurationProperties.value = await loadConfigurationProperties();
  }
}

function onCellClicked(params: CellClickedEvent) {
  if (isClickInActionsColumn(params)) {
    const action = (params.event?.target as HTMLElement).dataset.action;
    if (action === "delete") {
      deleteRow(params.data);
    } else if (action === "edit") {
      startEdit(params);
    }
  }
}

function isClickInActionsColumn(params: CellClickedEvent) {
  return (
    params.column.getColId() === "action" &&
    (params.event?.target as HTMLElement).dataset.action
  );
}

function onClearFilters() {
  gridApi.value?.setFilterModel(null);
}

function onClickImport() {
  isImportButtonDisabled.value = true;
  const fileInput = document.createElement("input");
  fileInput.type = "file";
  fileInput.accept = ".yaml";
  fileInput.onchange = async () => {
    if (fileInput.files && fileInput.files.length > 0) {
      try {
        const response = await importConfig(fileInput.files[0]);
        if (response.ok) {
          configurationProperties.value = await loadConfigurationProperties();
          importFileStatus.value = "";
        } else {
          const errorJson = await response.json();
          importFileStatus.value = `Something went wrong with the import: ${errorJson.error}`;
        }
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "An unknown error occurred";
        importFileStatus.value = `Something went wrong with the import: ${message}`;
      }
      fileInput.remove();
    }
  };
  fileInput.click();
  isImportButtonDisabled.value = false;
}

async function onClickRefresh() {
  isRefreshButtonDisabled.value = true;
  configurationProperties.value = await loadConfigurationProperties();
  isRefreshButtonDisabled.value = false;
}

async function deleteRow(data: TigerConfigurationPropertyDto) {
  const response = await deleteConfigurationProperty(data);
  if (response.ok) {
    configurationProperties.value = await loadConfigurationProperties();
  }
}

async function startEdit(params: CellClickedEvent) {
  if (params.rowIndex !== null) {
    params.api.startEditingCell({
      rowIndex: params.rowIndex,
      colKey: "value",
    });
  }
}

const defaultColDef: ColDef = {
  sortable: true,
  filter: true,
  editable: false,
  resizable: true,
  icons: {
    menu: '<i class="fa fa-filter"/>',
  },
};

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
    minWidth: 80,
  },
  {
    headerName: "Value",
    field: "value",
    colId: "value",
    cellRenderer: ConfigurationValueCell,
    cellEditorPopup: true,
    cellEditor: ConfigurationValueCellEditor,
    cellEditorPopupPosition: "over",
    flex: 8,
    autoHeight: true,
    editable: true,
    minWidth: 90,
  },
  {
    headerName: "Action",
    cellRenderer: EditActionButtons,
    colId: "action",
    cellClass: "text-end",
    flex: 1,
    filter: false,
    minWidth: 83,
  },
];
</script>

<template>
  <div class="config-editor container flex items-center">
    <div class="text-start py-1">
      <button
        id="test-tg-config-editor-btn-clear-filters"
        class="btn btn-outline-secondary btn-sm me-1"
        type="button"
        title="clear the filters applied to the table"
        @click.prevent="onClearFilters"
      >
        Clear filters
      </button>
      <a
        id="test-tg-config-editor-btn-export"
        class="btn btn-outline-secondary btn-sm me-1"
        type="button"
        href="/global_configuration/file"
        download="global_configuration.json"
        title="export the configuration as a file"
        >Export
      </a>

      <button
        id="test-tg-config-editor-btn-import"
        class="btn btn-outline-secondary btn-sm me-1"
        type="button"
        :disabled="isImportButtonDisabled"
        title="import a configuration file"
        @click.prevent="onClickImport"
      >
        Import
      </button>

      <button
        id="test-tg-config-editor-btn-refresh"
        class="btn btn-outline-secondary btn-sm me-1"
        type="button"
        :disabled="isRefreshButtonDisabled"
        title="refresh the configuration"
        @click.prevent="onClickRefresh"
      >
        Refresh
      </button>
    </div>
    <div v-if="importFileStatus" class="alert alert-danger" role="alert">
      <i class="fas fa-exclamation-triangle"></i> {{ importFileStatus }}
    </div>
    <ag-grid-vue
      :modules="modules"
      id="test-tg-config-editor-table"
      ref="editorGrid"
      class="ag-theme-alpine editor-table"
      :row-data="configurationProperties"
      :column-defs="columnDefs"
      :default-col-def="defaultColDef"
      :theme="themeAlpine"
      :suppress-click-edit="false"
      suppress-navigable="true"
      :suppress-menu-hide="true"
      cell-class="no-border"
      dom-layout="autoHeight"
      @cell-clicked="onCellClicked"
      @grid-ready="onGridReady"
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
