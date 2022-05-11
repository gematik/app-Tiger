<!--
  - Copyright (c) 2022 gematik GmbH
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
    <h4><i class="fa-solid fa-address-card left"></i>Testrun</h4>
    <div class="alert alert-info featurelistbox">
      <div class="alert-heading featurelist">
        <div v-for="feature in featureUpdateMap">
          <div class="truncate-text" :title="`${feature[1].description}`">
            <b>{{ feature[1].description }}</b>
          </div>
          <div v-for="scenario in feature[1].scenarios" class="container">
            <div class="truncate-text" :title="`${scenario[1].description}`">
              <div v-if="scenario[1].variantIndex == -1">
                {{ scenario[1].description }}
              </div>
              <div v-else>
                {{ scenario[1].description}} [{{scenario[1].variantIndex+1}}]  
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";

// @ts-ignore
const props = defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
}>();
</script>

<style>
.featurelistbox {
  padding: 0.25rem;
}

.featurelist {
  font-size: 75%;
}
</style>
