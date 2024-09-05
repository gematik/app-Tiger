/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {ref} from "vue";
import {runScenario} from "@/components/replay/ScenarioRunner";
import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";

export function useConfirmRun() {
  const dialogIsOpen = ref(false)

  function openDialog() {
    if (dialogIsOpen.value) {
      return;
    }
    dialogIsOpen.value = true;
  }

  function onClickConfirm(scenario: ScenarioIdentifier) {
    runScenario(scenario);
    dialogIsOpen.value = false;
  }

  function onClickDismiss() {
    dialogIsOpen.value = false;
  }

  return {openDialog, onClickConfirm, onClickDismiss, dialogIsOpen};
}


