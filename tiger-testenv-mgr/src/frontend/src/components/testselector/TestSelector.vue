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
import { computed, inject, ref, type Ref } from "vue";
import TreeTable, { type TreeTableFilterEvent } from "primevue/treetable";
import Column from "primevue/column";
import Tag from "primevue/tag";
import InputText from "primevue/inputtext";
import InputIcon from "primevue/inputicon";
import IconField from "primevue/iconfield";
import type { TreeNode } from "primevue/treenode";
import { useFeaturesStore } from "@/stores/features.ts";
import type { DynamicDialogInstance } from "primevue/dynamicdialogoptions";
import { convertToTreeNode } from "@/components/testselector/FeatureToTreeNodeConverter.ts";
import { visitTreeNodes } from "@/components/testselector/TreeNodeVisitor.ts";
import { runSelectedTests } from "@/components/replay/ScenarioRunner.ts";
import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier.ts";
import { useSelectedTestsStore } from "@/stores/selectedTests.ts";
import {
  relativizePaths,
  resolvePaths,
} from "@/components/testselector/PathsConverter.ts";
import { useTestSuiteLifecycleStore } from "@/stores/testSuiteLifecycle.ts";
import ProgressSpinner from "primevue/progressspinner";
import { useCollapseExpandedTests } from "@/stores/collapseExpandedTests.ts";
import Message from "primevue/message";
import Popover from "primevue/popover";
import TagsSelector from "@/components/testselector/TagsSelector.vue";

const thisDialogInstance = inject<Ref<DynamicDialogInstance>>("dialogRef");

const closeDialog = () => {
  thisDialogInstance?.value.close();
};

const featuresStore = useFeaturesStore();

const testsToSelect = computed(() =>
  convertToTreeNode(featuresStore.featureUpdateMap),
);

//we need a map of keys to booleans to indicate that all tree nodes are expanded on start
const extractKeys = (nodes: TreeNode[]): string[] => {
  const keys: string[] = [];

  visitTreeNodes(nodes, (node: TreeNode) => {
    if (node.key) {
      keys.push(node.key);
    }
  });
  return keys;
};

const expandedKeysStore = useCollapseExpandedTests();

//needed for easier management of the expanded keys
const allKeys = computed(() => extractKeys(testsToSelect.value));

const expandAll = () => {
  allKeys.value.forEach((key) => {
    expandedKeysStore.expandKey(key);
  });
};
const collapseAll = () => {
  expandedKeysStore.collapseAll();
};

const selectedTestsStore = useSelectedTestsStore();
const testSuiteLifecycleStore = useTestSuiteLifecycleStore();

const tags = computed(() => {
  const allTags = Array.from(featuresStore.featureUpdateMap.values())
    .flatMap((f) => Array.from(f.scenarios.values()))
    .flatMap((s) => s.tags);
  return new Set(allTags);
});

const executeSelection = () => {
  runSelectedTests();
  closeDialog();
};

const selectAll = () => {
  selectedTestsStore.replaceSelection(allKeys.value);
};

const selectNone = () => {
  selectedTestsStore.clearSelection();
};

function findWithTag(tag: string): string[] {
  const testIdsWithTag: string[] = [];
  visitTreeNodes(testsToSelect.value, (node: TreeNode) => {
    if (node.data.tags?.includes(tag)) {
      testIdsWithTag.push(node.key);
    }
  });
  return testIdsWithTag;
}

function addToSelection(tag: string) {
  const keysToAdd = findWithTag(tag);
  selectedTestsStore.addToSelection(keysToAdd);
  updateParentsCheckedStatus();
}

// When clicking on the checkboxes, the PrimeVue components takes care of it,
//but when changing selections via the tag buttons, we lose the information of which
//parent nodes are partially selected.
function updateParentsCheckedStatus() {
  const updatedStatus = { ...selectedTestsStore.allTestsSelectedStatus };

  // Recursively update each tree from the root
  testsToSelect.value.forEach((rootNode) => {
    updateNodeStatus(rootNode, updatedStatus);
  });

  selectedTestsStore.allTestsSelectedStatus = updatedStatus;
}

function updateNodeStatus(
  node: TreeNode,
  updatedStatus: Record<
    string,
    {
      checked: boolean;
      partialChecked: boolean;
    }
  >,
): { checked: boolean; partialChecked: boolean } {
  // Base case: leaf node
  if (!node.children || node.children.length === 0) {
    // Return the current status of this leaf node
    return updatedStatus[node.key] || { checked: false, partialChecked: false };
  }

  // Recursive case: process all children first
  const childStatuses = node.children.map((child) =>
    updateNodeStatus(child, updatedStatus),
  );

  // Determine this node's status based on its children
  const checkedChildren = childStatuses.filter((status) => status.checked);
  const partiallyCheckedChildren = childStatuses.filter(
    (status) => status.partialChecked,
  );

  let nodeStatus: { checked: boolean; partialChecked: boolean };

  if (checkedChildren.length === childStatuses.length) {
    // All children are checked
    nodeStatus = { checked: true, partialChecked: false };
  } else if (
    checkedChildren.length === 0 &&
    partiallyCheckedChildren.length === 0
  ) {
    // No children are checked or partially checked
    nodeStatus = { checked: false, partialChecked: false };
  } else {
    // Some children are checked or partially checked
    nodeStatus = { checked: false, partialChecked: true };
  }

  // Update the status for this node
  if (node.key) {
    if (nodeStatus.checked || nodeStatus.partialChecked) {
      updatedStatus[node.key] = nodeStatus;
    } else {
      delete updatedStatus[node.key];
    }
  }

  return nodeStatus;
}

function removeFromSelection(tag: string) {
  const keysToUnselect = findWithTag(tag);
  selectedTestsStore.removeFromSelection(keysToUnselect);
  updateParentsCheckedStatus();
}

function replaceSelection(tag: string) {
  const newSelectionIds = findWithTag(tag);
  selectedTestsStore.replaceSelection(newSelectionIds);
  updateParentsCheckedStatus();
}

function onFilterChange(_event: TreeTableFilterEvent) {
  //when changing the filters, the partially checked checkboxes are not updated correctly.
  updateParentsCheckedStatus();
}

async function saveSelection() {
  const relativizedPaths = await relativizePaths(
    selectedTestsStore.onlyScenariosAndScenarioVariants.map(
      (s) => new ScenarioIdentifier(s),
    ),
  );
  const dataStr = JSON.stringify(relativizedPaths, null, 2);
  const dataBlob = new Blob([dataStr], { type: "application/json" });
  const url = URL.createObjectURL(dataBlob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "selected-tests.json";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function loadSelection() {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "application/json";
  input.onchange = (event: Event) => {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = async (e) => {
        try {
          const selectedTests: ScenarioIdentifier[] = JSON.parse(
            e.target?.result as string,
          );
          const testsWithResolvedPaths = await resolvePaths(selectedTests);

          const justTestIds = testsWithResolvedPaths.map((s) => s.uniqueId);
          const { inA: testThatExist, notInA: doNotExist } =
            partitionByMembership(allKeys.value, justTestIds);
          selectedTestsStore.replaceSelection(testThatExist);
          if (doNotExist.length > 0) {
            errorMessage.value = [
              "When loading tests, the following tests were not found on the current test suite:",
              ...doNotExist,
            ];
          } else {
            errorMessage.value = [];
          }
          console.log(
            "Loaded selection:",
            selectedTestsStore.currentlySelectedTests,
          );
          updateParentsCheckedStatus();
        } catch (error) {
          console.error("Error loading selection:", error);
        }
      };
      reader.readAsText(file);
    }
  };
  input.click();
}

function partitionByMembership<T>(a: T[], b: T[]) {
  const aSet = new Set(a);
  const inA: T[] = [];
  const notInA: T[] = [];
  for (const item of b) {
    (aSet.has(item) ? inA : notInA).push(item);
  }
  return { inA, notInA };
}

const filters = ref<Record<string, string>>({});

const popover = ref<InstanceType<typeof Popover> | null>(null);
const currentOpenExample = ref<string | null>(null);

function openPopover(event: MouseEvent, examples: string) {
  currentOpenExample.value = examples;
  popover.value?.toggle(event); // anchors to the clicked button
}

const errorMessage = ref<string[]>([]);
</script>

<template>
  <div class="container" id="testselector-modal">
    <div class="dialog-header--sticky">
      <div class="d-flex justify-content-between align-items-center">
        <h1>Select tests to execute</h1>

        <button class="btn btn-primary" @click="executeSelection">
          Execute selected tests <i class="fa fa-play"></i>
        </button>
      </div>
    </div>
    <Message
      closable
      class="row mt-1"
      severity="warn"
      v-if="errorMessage.length > 0"
    >
      <span style="white-space: pre-line">{{ errorMessage[0] }}</span>
      <ul v-if="errorMessage.length > 1" class="mb-0">
        <li v-for="(msg, idx) in errorMessage.slice(1)" :key="idx">
          {{ msg }}
        </li>
      </ul>
    </Message>
    <div class="row mt-1">
      <div id="testselector-table">
        <div
          class="d-flex gap-1 align-items-center"
          id="testselector-action-buttons"
        >
          <button class="btn btn-sm btn-secondary" @click="selectAll">
            Select All
          </button>
          <button class="btn btn-sm btn-secondary" @click="selectNone">
            Select None
          </button>
          <button class="btn btn-sm btn-secondary" @click="expandAll">
            Expand All
          </button>
          <button class="btn btn-sm btn-secondary" @click="collapseAll">
            Collapse All
          </button>
          <button class="btn btn-sm btn-secondary" @click="saveSelection">
            Save selection
          </button>
          <button class="btn btn-sm btn-secondary" @click="loadSelection">
            Load selection
          </button>

          <div class="ms-auto">
            <IconField>
              <InputIcon class="pi pi-search" />
              <InputText
                size="small"
                v-model="filters['global']"
                placeholder="Global Search"
              />
            </IconField>
          </div>
        </div>
        <div class="col ps-2">
          <TagsSelector
            v-if="
              !testSuiteLifecycleStore.waitingForTestDiscovery && tags.size > 0
            "
            :tags="tags"
            @select-tag="addToSelection"
            @remove-tag="removeFromSelection"
            @replace-selection="replaceSelection"
          />
        </div>
        <div
          class="d-flex justify-content-center align-items-center"
          v-if="testSuiteLifecycleStore.waitingForTestDiscovery"
        >
          <ProgressSpinner
            class="ms-0 me-2 mt-1"
            style="width: 50px; height: 50px"
            strokeWidth="8"
            fill="transparent"
            animationDuration="5s"
          />
          <div>Waiting for test discovery</div>
        </div>
        <TreeTable
          v-else
          v-model:expanded-keys="expandedKeysStore.expandedState"
          v-model:selection-keys="selectedTestsStore.allTestsSelectedStatus"
          selectionMode="checkbox"
          :value="testsToSelect"
          :filters="filters"
          filterMode="lenient"
          @filter="onFilterChange"
          tableStyle="min-width: 50rem"
        >
          <Column
            field="label"
            header="Name"
            expander
            style="width: 50%"
            filter-match-mode="contains"
          >
            <template #body="slotProps">
              <div>
                <div>
                  {{ slotProps.node.data.label }}
                  <button
                    v-if="slotProps.node.data.examples"
                    class="btn btn-outline-secondary btn-sm"
                    @click="openPopover($event, slotProps.node.data.examples)"
                  >
                    <i class="fa-solid fa-arrow-up-right-from-square"></i>
                  </button>
                </div>

                <Popover v-if="slotProps.node.data.examples"> </Popover>
              </div>
            </template>
            <template #filter>
              <InputText
                size="small"
                v-model="filters['label']"
                type="text"
                placeholder="Filter by name"
              />
            </template>
          </Column>

          <Column
            v-if="tags.size > 0"
            field="tags"
            header="Tags"
            style="width: 25%"
            filter-match-mode="contains"
          >
            <template #body="slotProps">
              <div>
                <Tag
                  v-for="tag in slotProps.node.data.tags"
                  class="me-1"
                  :key="tag"
                  :value="tag"
                  rounded
                ></Tag>
              </div>
            </template>
            <template #filter>
              <InputText
                size="small"
                v-model="filters['tags']"
                type="text"
                placeholder="Filter by tag"
              />
            </template>
          </Column>
          <Column
            field="sourcePath"
            header="Source File"
            style="width: 25%"
            filter-match-mode="contains"
          >
            <template #filter>
              <InputText
                size="small"
                v-model="filters['sourcePath']"
                type="text"
                placeholder="Filter by file"
              />
            </template>
          </Column>
        </TreeTable>
      </div>
    </div>
    <Popover ref="popover">
      <div>
        <pre class="text-muted small">{{ currentOpenExample }}</pre>
      </div>
    </Popover>
  </div>
</template>

<style scoped>
.dialog-header--sticky {
  position: sticky;
  top: 0;
  background-color: var(--p-dialog-background);
  z-index: 1055;
}
</style>
