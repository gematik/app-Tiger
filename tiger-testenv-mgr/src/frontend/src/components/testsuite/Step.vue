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
import type Ui from "@/types/ui/Ui.ts";
import type {IStep} from "@/types/testsuite/StepUpdate.ts";

import {ref} from 'vue';
import FailureMessage from "@/components/testsuite/FailureMessage.vue";
import {getTestResultIcon} from "@/types/testsuite/TestResult.ts";

defineProps<{
  step: IStep;
  ui: Ui;
  ariaLabel: string;
}>();

const isVisible = ref(false);

function toggleTable() {
  isVisible.value = !isVisible.value;
}
</script>

<template>
  <div class="test-step-container"
       :aria-label="ariaLabel">
    <table>
      <tbody>
      <tr>
        <td>
          <div v-if="step.subSteps.length > 0"
               class="test-step-toggle">
            <button
                class="btn btn-outline-primary test-step-toggle-button"
                @click="toggleTable"
                title="Toggle sub step visibility"
            >
              <i :class="isVisible ? 'fas fa-minus-circle' : 'fas fa-plus-circle'"/>
            </button>
          </div>
        </td>
        <td>
          <div class="test-step-line">
            <div class="test-step-description"
                 :title="step.tooltip"
                 v-html="step.description"
            />
          </div>
        </td>
      </tr>
      <tr v-show="isVisible"
          v-for="subStep in step.subSteps" :key="subStep.stepIndex">
        <td
            :class="`${subStep.status.toLowerCase()} step_status test-step-status-${subStep.status.toLowerCase()}`"
        >
          <div class="test-status-icon">
            <i
                :class="`fa-solid ${getTestResultIcon(subStep.status, 'solid')}`"
                :title="subStep.status"
            />
          </div>
        </td>
        <td>
          <Step :step="subStep"
                :ui="ui"
                ariaLabel="Sub-steps performed when executing this test step"
          />
        </td>
      </tr>
      </tbody>
    </table>
    <FailureMessage
        v-if="step.failureMessage"
        :message="step.failureMessage"
        :stacktrace="step.failureStacktrace"
    />
  </div>
</template>

<style scoped>
button.btn.btn-outline-primary {
  font-size: 1.0rem;
  border-radius: 0.5rem; /* Optional: reduce border radius */
  border: none;
  padding: 0 0;
  line-height: 1;
  margin-top: 0;
  margin-left: 0;
}

.test-step-line {
  display: flex;
  align-items: flex-start; /* Align items vertically */
}

.test-step-toggle {
  margin-right: 0.5rem; /* Adjust spacing */
  justify-content: flex-start; /* Center horizontally */
  display: flex;
  margin-left: 0;
  padding-left: 0;
}

.test-status-icon {
  font-size: 1.0rem;
  display: flex;
  justify-content: flex-start; /* Center horizontally */
  margin-left: 0;
  padding-left: 0;
}

/* Match the icon size in both components */
.test-status-icon i {
  font-size: 1.0rem;
}

.step_status {
  text-align: center;
  vertical-align: top;
}

td {
  vertical-align: top; /* Align all table cells to top */
}

/* Target the first column specifically for consistent width and alignment */
tr td:first-child {
  text-align: left;
  padding-left: 0;
}
</style>