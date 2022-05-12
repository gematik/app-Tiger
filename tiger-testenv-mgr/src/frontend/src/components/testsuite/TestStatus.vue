<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div class="container">
    <div class="alert alert-light featurelistbox" :aria-label="`${createStats(featureUpdateMap)}`">
      <div class="alert-heading featurelist">
        <div v-if="features.failed > 0" class="pl-3 failed fw-bold">Features: {{ features.passed }} OK {{features.failed}} FAIL</div>
        <div v-else class="pl-3 passed">Features: {{ features.passed }} OK</div>
        <div v-if="scenarios.failed > 0" class="pl-3 failed fw-bold">Scenarios: {{ scenarios.passed }} OK {{scenarios.failed}} FAIL</div>
        <div v-else class="pl-3 passed">Scenarios: {{ scenarios.passed }} OK</div>
        <div class="mt-2 small text-muted">Started: {{ started }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import TestResult from "@/types/testsuite/TestResult";

const started = new Date();

const features = {
  passed: 0,
  failed: 0
};

const scenarios = {
  passed: 0,
  failed: 0
}

// @ts-ignore
const props = defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
}>();

function createStats(map : Map<string, FeatureUpdate>) {
  features.passed = 0;
  features.failed = 0;
  scenarios.passed = 0;
  scenarios.failed = 0;
  map.forEach(feature => {
    if (feature.status === TestResult.FAILED) features.failed++;
    if (feature.status === TestResult.PASSED) features.passed++;
    feature.scenarios.forEach(scenario => {
      if (scenario.status === TestResult.FAILED) scenarios.failed++;
      if (scenario.status === TestResult.PASSED) scenarios.passed++;
    })
  })
}
</script>

<style>
.featurelistbox {
  padding: 0.5rem;
}
</style>
