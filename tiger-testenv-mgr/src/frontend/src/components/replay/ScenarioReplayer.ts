/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";

export function replayScenario(scenarioIdentifier: ScenarioIdentifier) {
  fetch(process.env.BASE_URL + "replay", {
    headers: new Headers({'content-type': 'application/json'}),
    method: "POST",
    body: JSON.stringify(scenarioIdentifier)
  });
}