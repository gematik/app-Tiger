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

<script setup lang="ts">

import ConfirmDialog from "@/components/dialogs/ConfirmDialog.vue";
import {useConfirmRun} from "@/components/replay/ConfirmRun";
import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";


const props = defineProps<{
  scenario: ScenarioIdentifier
  showPlayButton: boolean
}>();

const {openDialog, onClickConfirm, onClickDismiss, dialogIsOpen} = useConfirmRun();

</script>

<template>
  <button v-if="props.showPlayButton" @click="openDialog" class="btn btn-outline-secondary btn-sm play-button"
          title="Run Scenario"><i
      class="fa fa-play"></i> Run
  </button>
  <button v-else @click="openDialog" class="btn btn-outline-secondary btn-sm replay-button"
          title="Replay Scenario"><i
      class="fa fa-rotate-right"></i> Replay
  </button>


  <confirm-dialog :dialog-is-open="dialogIsOpen"
                  @click-confirm="() => onClickConfirm(props.scenario)"
                  @click-dismiss="onClickDismiss"
                  :header="props.showPlayButton? 'Run Scenario': 'Replay Scenario'"
                  description=""
                  :label-confirm-button="props.showPlayButton? 'Yes, run' : 'Yes, replay'"
                  label-dismiss-button="Cancel">

  </confirm-dialog>
</template>
