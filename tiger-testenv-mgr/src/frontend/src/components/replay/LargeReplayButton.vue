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

import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";
import ConfirmDialog from "@/components/dialogs/ConfirmDialog.vue";
import {useConfirmReplay} from "@/components/replay/ConfirmReplay";


const props = defineProps<{
  scenario: ScenarioIdentifier
}>();

const {openDialog, onClickConfirm, onClickDismiss, dialogIsOpen} = useConfirmReplay();

</script>

<template>
  <button @click="openDialog" class="btn btn-outline-secondary btn-sm replay-button" title="Replay Scenario"><i
      class="fa fa-rotate-right"></i> Replay
  </button>

  <confirm-dialog :dialog-is-open="dialogIsOpen"
                  @click-confirm="() => onClickConfirm(props.scenario)"
                  @click-dismiss="onClickDismiss"
                  header="Replay Scenario?"
                  description=""
                  label-confirm-button="Yes, replay"
                  label-dismiss-button="Cancel">

  </confirm-dialog>
</template>
