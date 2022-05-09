<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
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
