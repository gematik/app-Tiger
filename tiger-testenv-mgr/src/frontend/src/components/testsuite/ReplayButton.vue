<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<script setup lang="ts">

import ScenarioIdentifier from "@/types/testsuite/ScenarioIdentifier";

const props = defineProps<{
  scenario: ScenarioIdentifier;
}>()

function replayScenario() {
  fetch(process.env.BASE_URL + "replay", {
    headers: new Headers({'content-type': 'application/json'}),
    method: "POST",
    body: JSON.stringify(props.scenario)
  });
}

</script>

<template>
  <button @click="replayScenario()" class="btn btn-outline-secondary btn-sm replay-button" title="Replay Scenario"><i
      class="fa fa-rotate-right"></i>Replay
  </button>
</template>
