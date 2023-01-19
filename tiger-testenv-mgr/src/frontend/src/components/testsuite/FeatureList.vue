<!--
  - Copyright (c) 2023 gematik GmbH
  - 
  - Licensed under the Apache License, Version 2.0 (the License);
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  - 
  -     http://www.apache.org/licenses/LICENSE-2.0
  - 
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an 'AS IS' BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="container">
    <div class="alert alert-light featurelistbox">
      <div class="alert-heading featurelist">
        <div v-for="(feature, key) in featureUpdateMap" :key="key">
          <div class="truncate-text" :title="`${feature[1].description}`">
            <b>{{ feature[1].description }}</b>
          </div>
          <div v-for="(scenario, key) in feature[1].scenarios" :key="key" class="container">
            <div class="truncate-text" :title="`${scenario[1].description}`">
              <a class="scenarioLink" v-if="scenario[1].variantIndex === -1" :href="'#' + scenario[1].getLink(feature[1].description)">{{ scenario[1].description }}</a>
              <a class="scenarioLink" v-else :href="'#' + scenario[1].getLink(feature[1].description)">{{ scenario[1].description }} [{{scenario[1].variantIndex+1}}]</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";

defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
}>();
</script>

<style>
.featurelistbox {
  padding: 0.5rem;
}

.featurelist {
  font-size: 85%;
}
.scenarioLink {
  text-decoration: none;
  color: var(--gem-primary-400);
}
</style>
