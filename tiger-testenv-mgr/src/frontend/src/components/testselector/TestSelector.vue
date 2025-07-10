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
import { computed, inject, type Ref, ref } from "vue";
import TreeTable from "primevue/treetable";
import Column from "primevue/column";
import type { TreeNode } from "primevue/treenode";
import { useFeaturesStore } from "@/stores/features.ts";
import type { DynamicDialogInstance } from "primevue/dynamicdialogoptions";
import { convertToTreeNode } from "@/components/testselector/FeatureToTreeNodeConverter.ts";
import { visitTreeNodes } from "@/components/testselector/TreeNodeVisitor.ts";
import { runScenarios } from "@/components/replay/ScenarioRunner.ts";
import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier.ts";

const thisDialogInstance = inject<Ref<DynamicDialogInstance>>("dialogRef");

const closeDialog = () => {
  thisDialogInstance?.value.close();
};

const featuresStore = useFeaturesStore();

const testsToSelect = computed(() =>
  convertToTreeNode(featuresStore.featureUpdateMap),
);

//we need a map of keys to booleans to indicate that all tree nodes are expanded on start
const extractKeysAsObject = (nodes: TreeNode[]): Record<string, boolean> => {
  const keys: Record<string, boolean> = {};

  visitTreeNodes(nodes, (node: TreeNode) => {
    if (node.key) {
      keys[node.key] = true;
    }
  });
  return keys;
};

const expandedKeys = computed(() => extractKeysAsObject(testsToSelect.value));
const checkedKeys = ref<
  Record<string, { checked: boolean; partiallyChecked: boolean }>
>({});

const executeSelection = () => {
  const selectedKeys = Object.keys(checkedKeys.value).filter(
    (key) => checkedKeys.value[key].checked,
  );

  const selectionUniqueIds: string[] = [];
  visitTreeNodes(testsToSelect.value, (node: TreeNode) => {
    if (
      (node.data.testType === "scenario" ||
        node.data.testType === "scenarioVariant") &&
      selectedKeys.includes(node.key)
    ) {
      selectionUniqueIds.push(node.key);
    }
  });
  runScenarios(selectionUniqueIds.map((s) => new ScenarioIdentifier(s)));
  closeDialog();
};
</script>

<template>
  <div class="container">
    <div class="row dialog-header--sticky">
      <h1>Select tests to execute</h1>
      <button class="btn btn-primary" @click="executeSelection">
        Execute selected tests
      </button>
    </div>
    <div class="row">
      <TreeTable
        :expanded-keys="expandedKeys"
        v-model:selection-keys="checkedKeys"
        selectionMode="checkbox"
        :value="testsToSelect"
        tableStyle="min-width: 50rem"
      >
        <Column field="label" header="Name" expander style="width: 34%">
          <template #body="slotProps">
            <div>
              <div>{{ slotProps.node.data.label }}</div>
              <pre
                v-if="slotProps.node.data.examples"
                class="text-muted small"
                >{{ slotProps.node.data.examples }}</pre
              >
            </div>
          </template>
        </Column>

        <Column
          field="sourcePath"
          header="Source File"
          style="width: 33%"
        ></Column>
      </TreeTable>
    </div>
  </div>
</template>

<style scoped>
.dialog-header--sticky {
  position: sticky;
  top: 0;
  background-color: var(--p-dialog-background); /* [1] */
  z-index: 1055; /* [2] */
}
</style>
