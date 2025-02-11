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

<template>
  <div class="container">
    <div
      id="test-sidebar-statusbox"
      class="alert alert-light featurelistbox engraved"
      :aria-label="`${createStats(featureUpdateMap)}`"
    >
      <div class="alert-heading featurelist">
        <div
          v-if="features.failed > 0"
          class="pl-3 fw-bold test-sidebar-status-features"
        >
          Features: {{ features.passed }} OK {{ features.failed }} FAIL
        </div>
        <div v-else class="pl-3 test-sidebar-status-features">
          Features: {{ features.passed }} OK
        </div>
        <div
          v-if="scenarios.failed > 0"
          class="pl-3 fw-bold test-sidebar-status-scenarios"
        >
          Scenarios: {{ scenarios.passed }} OK {{ scenarios.failed }} FAIL
        </div>
        <div v-else class="pl-3 test-sidebar-status-scenarios">
          Scenarios: {{ scenarios.passed }} OK
        </div>
        <div id="test-sidebar-status-started" class="mt-2 small text-muted">
          Started: {{ started }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import TestResult from "@/types/testsuite/TestResult";

const features = {
  passed: 0,
  failed: 0,
};

const scenarios = {
  passed: 0,
  failed: 0,
};

defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
  started: Date;
}>();

function createStats(map: Map<string, FeatureUpdate>) {
  features.passed = 0;
  features.failed = 0;
  scenarios.passed = 0;
  scenarios.failed = 0;
  map.forEach((feature) => {
    if (feature.status === TestResult.FAILED) features.failed++;
    if (feature.status === TestResult.PASSED) features.passed++;
    feature.scenarios.forEach((scenario) => {
      if (scenario.status === TestResult.FAILED) scenarios.failed++;
      if (scenario.status === TestResult.PASSED) scenarios.passed++;
    });
  });
}
</script>

<style>
.featurelistbox {
  padding: 0.5rem;
  color: var(--gem-primary-400);
}
</style>
