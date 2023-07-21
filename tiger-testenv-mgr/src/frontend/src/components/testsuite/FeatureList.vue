<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div class="container">
    <div class="alert alert-light engraved featurelistbox" id="test-sidebar-featurelistbox">
      <div class="alert-heading featurelist">
        <div v-for="(feature, key) in featureUpdateMap" :key="key">
          <div class="truncate-text test-sidebar-feature-name" :title="`${feature[1].description}`">
            <b>{{ feature[1].description }}</b>
          </div>
          <div v-for="(scenario, key) in feature[1].scenarios" :key="key" class="container">
            <div class="truncate-text test-sidebar-scenario-name" :title="`${scenario[1].description}`">
              <a class="scenarioLink" :href="'#' + scenario[1].getLink(feature[1].description)">
                <i :class="`${scenario[1].status.toLowerCase()} ${getTestResultIcon(scenario[1].status, 'regular')}`"></i>
                &nbsp;{{ scenario[1].description }}&nbsp;
                <span class="test-sidebar-scenario-index" v-if="scenario[1].variantIndex !== -1">[{{ scenario[1].variantIndex + 1 }}]</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import {getTestResultIcon} from "@/types/testsuite/TestResult";

defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
}>();
</script>

<style>
.featurelistbox {
    padding: 0.5rem;
    color: var(--gem-primary-400);
}

.featurelist {
    font-size: 85%;
}

.scenarioLink {
    text-decoration: none;
    color: var(--gem-primary-400);
}
</style>
