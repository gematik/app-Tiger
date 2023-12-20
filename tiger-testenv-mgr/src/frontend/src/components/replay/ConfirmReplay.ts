/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */


import {ref} from "vue";
import {replayScenario} from "@/components/replay/ScenarioReplayer";
import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";

export function useConfirmReplay() {
  const dialogIsOpen = ref(false)

  function openDialog() {
    if (dialogIsOpen.value) {
      return;
    }
    dialogIsOpen.value = true;
  }

  function onClickConfirm(scenario: ScenarioIdentifier) {
    replayScenario(scenario);
    dialogIsOpen.value = false;
  }

  function onClickDismiss() {
    dialogIsOpen.value = false;
  }

  return {openDialog, onClickConfirm, onClickDismiss, dialogIsOpen};
}


