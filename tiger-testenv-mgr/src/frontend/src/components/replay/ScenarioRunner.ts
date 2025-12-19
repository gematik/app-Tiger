///
///
/// Copyright 2021-2025 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";
import { useSelectedTestsStore } from "@/stores/selectedTests.ts";

export function runScenario(scenarioIdentifier: ScenarioIdentifier) {
  fetch(import.meta.env.BASE_URL + "run", {
    headers: new Headers({ "content-type": "application/json" }),
    method: "POST",
    body: JSON.stringify(scenarioIdentifier),
  });
}

export function runScenarios(scenarioIdentifiers: ScenarioIdentifier[]) {
  if (scenarioIdentifiers.length === 0) {
    return;
  }
  fetch(import.meta.env.BASE_URL + "run/selection", {
    headers: new Headers({ "content-type": "application/json" }),
    method: "POST",
    body: JSON.stringify(scenarioIdentifiers),
  });
}

export function runSelectedTests() {
  const selectedTests =
    useSelectedTestsStore().onlyScenariosAndScenarioVariants;
  runScenarios(selectedTests.map((e) => new ScenarioIdentifier(e)));
}
