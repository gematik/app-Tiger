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
import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";
import {ref} from "vue";
import ConfirmDialog from "@/components/dialogs/ConfirmDialog.vue";
import {useConfirmRun} from "@/components/replay/ConfirmRun";

const isHovering = ref(false);

const props = defineProps<{
  scenario: ScenarioIdentifier;
  showPlayButton: boolean;
}>();


const {openDialog, onClickConfirm, onClickDismiss, dialogIsOpen} =
    useConfirmRun();
</script>

<template>
  <div>
    <div
        v-if="props.showPlayButton"
        role="button"
        class="btn btn-link p-0 small-play-button test-play-small-button"
        title="Run Scenario"
        @click="openDialog"
    >
      <span
          class="fa-stack fa-2xs circle-arrow d-flex"
          style="flex-shrink: 0"
          @mouseenter="isHovering = true"
          @mouseleave="isHovering = false"
      >
        <i
            class="fa fa-regular fa-circle fa-stack-2x outer-circle"
            :class="{ 'fa-solid': isHovering }"
        ></i>
        <i
            class="fas fa-play fa-stack-1x inner-arrow"
            :class="{ 'fa-inverse': isHovering }"
        ></i>
      </span>
    </div>

    <div
        v-else
        role="button"
        class="btn btn-link p-0 small-play-button"
        title="Replay Scenario"
        @click="openDialog"
    >
      <span
          class="fa-stack fa-2xs circle-arrow d-flex"
          style="flex-shrink: 0"
          @mouseenter="isHovering = true"
          @mouseleave="isHovering = false"
      >
        <i
            class="fa fa-regular fa-circle fa-stack-2x outer-circle"
            :class="{ 'fa-solid': isHovering }"
        ></i>
        <i
            class="fas fa-rotate-right fa-stack-1x inner-arrow"
            :class="{ 'fa-inverse': isHovering }"
        ></i>
      </span>
    </div>

    <confirm-dialog
        :dialog-is-open="dialogIsOpen"
        :header="props.showPlayButton ? 'Run Scenario' : 'Replay Scenario'"
        description=""
        :label-confirm-button="props.showPlayButton ? 'Yes, run' : 'Yes, replay'"
        label-dismiss-button="Cancel"
        @click-confirm="() => onClickConfirm(props.scenario)"
        @click-dismiss="onClickDismiss"
    >
    </confirm-dialog>
  </div>
</template>

<style scoped>
.fa-stack.circle-arrow {
  font-size: 8px;
}

.outer-circle {
  color: var(--gem-primary-400);
}

.inner-arrow:not(.fa-inverse) {
  color: var(--gem-primary-400);
}
</style>
