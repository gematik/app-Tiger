<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
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
