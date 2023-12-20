<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<script setup lang="ts">

import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";
import {ref} from "vue";
import ConfirmDialog from "@/components/dialogs/ConfirmDialog.vue";
import {useConfirmReplay} from "@/components/replay/ConfirmReplay";

const isHovering = ref(false)

const props = defineProps<{
  scenario: ScenarioIdentifier
}>();

const {openDialog, onClickConfirm, onClickDismiss, dialogIsOpen} = useConfirmReplay();
</script>

<template>
  <div>
    <div @click="openDialog" role="button" class="btn btn-link p-0 small-replay-button"
         title="Replay Scenario">
    <span @mouseenter="isHovering = true" @mouseleave="isHovering = false" class="fa-stack fa-2xs circle-arrow d-flex"
          style="flex-shrink: 0;">
      <i class="fa fa-regular fa-circle fa-stack-2x outer-circle" :class="{'fa-solid': isHovering}"></i>
      <i class="fas fa-rotate-right fa-stack-1x inner-arrow" :class="{'fa-inverse': isHovering}"></i>
    </span>
    </div>

    <confirm-dialog :dialog-is-open="dialogIsOpen"
                    @click-confirm="() => onClickConfirm(props.scenario)"
                    @click-dismiss="onClickDismiss"
                    header="Replay Scenario?"
                    description=""
                    label-confirm-button="Yes, replay"
                    label-dismiss-button="Cancel">

    </confirm-dialog>
  </div>
</template>

<style scoped>

.fa-stack.circle-arrow {
  font-size: 8px;
}

.outer-circle {
  color: var(--gem-primary-400)
}

.inner-arrow:not(.fa-inverse) {
  color: var(--gem-primary-400)
}
</style>
