<!--


    Copyright 2021-2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    *******

    For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.

-->
<template>
  <div class="container">
    <div
        id="test-sidebar-featurelistbox"
        class="alert alert-light engraved featurelistbox"
    >
      <div class="alert-heading featurelist">
        <div v-for="(feature, key) in featureUpdateMap" :key="key">
          <div class="feature-list-header">
            <i v-if="feature[1].status === 'FAILED'"
               :class="`statusbadge ${feature[1].status.toLowerCase()} left fa-triangle-exclamation fa-solid`"
               :title="feature[1].computeStatusMessage()"
            ></i>
            <div
                class="truncate-text test-sidebar-feature-name"
                :title="`${feature[1].description}`"
            >
              <b>{{ feature[1].description }}</b>
            </div>
          </div>
          <div
              v-for="(scenario, key) in feature[1].scenarios"
              :key="key"
              class="container"
          >
            <div
                class="test-sidebar-scenario-name d-flex align-items-center"
                :title="`${scenario[1].description}`"
            >
              <div class="truncate-text">
                <a
                    v-if="scenario[1].status === 'FAILED'"
                    class="failureLink"
                    :href="'#' + scenario[1].getFailureId(feature[1].description)"
                >
                  <i
                      :class="`${scenario[1].status.toLowerCase()} ${getTestResultIcon(scenario[1].status, 'regular')}`"
                      :title="getStatusMessage(scenario[1].status, scenario[1].failureMessage)"
                  ></i>
                </a>
                <i
                    v-else
                    :class="`${scenario[1].status.toLowerCase()} ${getTestResultIcon(scenario[1].status, 'regular')}`"
                    :title="scenario[1].status"
                ></i>
                <a
                    class="scenarioLink"
                    :href="'#' + scenario[1].getLink(feature[1].description)"
                >
                  &nbsp;{{ scenario[1].description }}&nbsp;
                  <span
                      v-if="scenario[1].variantIndex !== -1"
                      class="test-sidebar-scenario-index"
                  >[{{ scenario[1].variantIndex + 1 }}]</span
                  >
                </a>
              </div>
              <small-play-button
                  class="ms-1"
                  :scenario="scenario[1].getScenarioIdentifier()"
                  :show-play-button="scenario[1].isDryRun"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import SmallPlayButton from "@/components/replay/SmallPlayButton.vue";
import {getTestResultIcon} from "@/types/testsuite/TestResult.ts";

defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
}>();

function getStatusMessage(
    status: string,
    failureMessage: string,
): string {
  if (status === "FAILED" || status === "ERROR") {
    return `${status}: ${failureMessage}`;
  } else {
    return status;
  }
}
</script>

<style>
.featurelistbox {
  padding: 0.5rem;
  color: var(--gem-primary-400);
}

.featurelist {
  font-size: 85%;
}

.feature-list-header {
  display: flex;
  align-items: center; /* Align items vertically */
}

i.statusbadge {
  font-size: 1.25rem;
  padding-right: 5rem;
}
</style>
