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
<template>
  <div class="container">
    <div
      id="test-sidebar-featurelistbox"
      class="alert alert-light engraved featurelistbox"
    >
      <Tree
        v-model:selection-keys="selectedTests.allTestsSelectedStatus"
        v-model:expanded-keys="expandedKeys"
        selectionMode="checkbox"
        :value="testsToSelectTreeNodes"
        tableStyle="width: 100%;"
        unstyled
        :pt="{
          rootChildren: 'root-children',
          nodeChildren: 'node-children',
          nodeContent: {
            class: 'd-flex w-100 align-items-center',
          },
          nodeLabel: {
            style: 'min-width: 0',
          },
          nodeToggleButton: ({ context }) => ({
            class: [
              'border-0 bg-transparent p-0 me-2 d-flex align-items-center justify-content-center',
              { invisible: context.leaf },
            ],
            style: 'width: 0.7rem; height: 0.7rem; flex-shrink:0',
          }),
          nodeToggleIcon: 'w-100 h-100',
          pcNodeCheckbox: {
            root: 'd-flex align-items-center justify-content-center',
            box: 'checkbox-virtual-box',
            input: 'checkbox-input',
          },
        }"
        class="alert-heading featurelist"
      >
        <template #feature="slotProps">
          <div class="feature-list-header">
            <i
              v-if="
                featureUpdateMap.get(slotProps.node.key)?.status === 'FAILED'
              "
              :class="`statusbadge ${featureUpdateMap.get(slotProps.node.key)?.status.toLowerCase()} left fa-triangle-exclamation fa-solid`"
              :title="
                featureUpdateMap.get(slotProps.node.key)?.computeStatusMessage()
              "
            ></i>
            <div
              class="truncate-text test-sidebar-feature-name"
              :title="slotProps.node.label"
            >
              <b>{{ slotProps.node.label }}</b>
            </div>
          </div>
        </template>
        <template #default="slotProps">
          <!-- iterating over 1 scenario, so that we get it as local variable, and dont
          need to call the same method over and over again -->
          <div
            v-for="(scenario, index) in [getScenario(slotProps.node.key)]"
            :key="index"
            class="test-sidebar-scenario-name d-flex align-items-center w-100"
            :title="slotProps.node.label"
            @click="(event) => event.stopImmediatePropagation()"
          >
            <a
              v-if="scenario?.status === 'FAILED'"
              class="failureLink"
              :href="'#' + scenario.getFailureId()"
              ><i
                :class="getIconClassForNode(slotProps.node)"
                :title="
                  getStatusMessage(scenario.status, scenario.failureMessage)
                "
              ></i>
            </a>
            <i
              v-else
              :class="getIconClassForNode(slotProps.node)"
              :title="scenario?.status"
            ></i>
            <div class="truncate-text">
              <a
                :class="{
                  scenarioLink: true,
                  'test-sidebar-scenario-with-index':
                    slotProps.node.data.testType === 'scenarioVariant',
                }"
                :href="'#' + (scenario ? scenario.getLink() : '')"
                >&nbsp;{{ slotProps.node.label }}&nbsp;</a
              >
            </div>
            <small-play-button
              class="ms-1"
              v-if="scenario"
              :scenario="scenario.getScenarioIdentifier()"
              :show-play-button="scenario.isDryRun"
            />
          </div>
        </template>
      </Tree>
    </div>
  </div>
</template>

<script setup lang="ts">
import SmallPlayButton from "@/components/replay/SmallPlayButton.vue";
import { getTestResultIcon } from "@/types/testsuite/TestResult.ts";
import { useSelectedTestsStore } from "@/stores/selectedTests.ts";
import { useFeaturesStore } from "@/stores/features.ts";
import { computed, type Ref, ref, watch } from "vue";
import { convertToTreeNode } from "@/components/testselector/FeatureToTreeNodeConverter.ts";
import Tree from "primevue/tree";
import type { TreeNode } from "primevue/treenode";
import type ScenarioUpdate from "@/types/testsuite/ScenarioUpdate.ts";
import { visitTreeNodes } from "@/components/testselector/TreeNodeVisitor.ts";

const featureUpdateMap = useFeaturesStore().featureUpdateMap;
const selectedTests = useSelectedTestsStore();
const testsToSelectTreeNodes = computed(() =>
  convertToTreeNode(featureUpdateMap, true),
);

const expandedKeys: Ref<Record<string, boolean>> = ref({});

const expandAll = () => {
  const toExpand: Record<string, boolean> = {};
  visitTreeNodes(testsToSelectTreeNodes.value, (node) => {
    toExpand[node.key] = true;
  });
  expandedKeys.value = toExpand;
};

function getScenario(id: string): ScenarioUpdate | undefined {
  return useFeaturesStore().getScenarioOrVariantById(id);
}

//expanded at the begining.
watch(testsToSelectTreeNodes, () => expandAll());

function getStatusMessage(status: string, failureMessage: string): string {
  if (status === "FAILED" || status === "ERROR") {
    return `${status}: ${failureMessage}`;
  } else {
    return status;
  }
}

function getIconClassForNode(treeNode: TreeNode): string {
  const scenario = getScenario(treeNode.key);
  if (scenario) {
    return (
      scenario.status.toLowerCase() +
      " " +
      getTestResultIcon(scenario.status, "regular")
    );
  } else {
    return "";
  }
}
</script>

<style>
.featurelistbox {
  padding: 0.5rem;
  color: var(--gem-primary-400);
}

.featurelist {
  font-size: 85%;
}

.feature-list-header {
  display: flex;
  align-items: center; /* Align items vertically */
}

i.statusbadge {
  font-size: 1.25rem;
  padding-right: 5rem;
}

.scenarioLink {
  text-decoration: none;
  color: var(--gem-primary-400);
}

li {
  list-style: none;
}

.root-children {
  padding: 0;
}

.node-children {
  padding-left: 0.5rem;
}

.checkbox-input {
  position: absolute;
  opacity: 0;
  margin: 0;
  padding: 0;
  z-index: 1; /* Ensure it stays on top to be clickable */
  cursor: pointer;
  appearance: none; /* Removes native styling */
}

.checkbox-virtual-box {
  width: 1rem; /* Adjust size as needed */
  height: 1rem;
  border: 1px solid #ced4da; /* Default border color */
  border-radius: 4px;
  background-color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  box-sizing: border-box;
}
</style>
