<template>
  <div class="container">
    <div>Featurelist</div>
    <div class="alert alert-info featurelistbox">
      <div class="alert-heading featurelist">
        <div v-for="(feature, key) in featureUpdateMap"
             :key="key"
        >
          <div v-for="(featureName, featureKey) in feature"
               :key="featureKey"
          >
            <div v-if="featureKey == 1" class="truncate-text" :title="`${featureName.description}`">
              <b>{{ featureName.description }}</b>
            </div>
            <div class="container"
                 v-for="(scenario, scenarioKey) in featureName.scenarios"
                 :key="scenarioKey"
            >
              <div v-for="(scenarioName, scenarioNameKey) in scenario"
                   :key="scenarioNameKey"
              >
                <div v-if="scenarioNameKey == 1" class="truncate-text" :title="`${scenarioName.description}`">
                  {{ scenarioName.description }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import FeatureUpdate from "@/types/FeatureUpdate";

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
