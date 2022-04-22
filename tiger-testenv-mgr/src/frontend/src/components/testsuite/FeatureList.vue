<template>
  <div>
    <br />
    <h5>Features and Scenarios:</h5>
    <div
      class="container"
      v-for="(feature, key) in featureUpdateMap"
      :key="key"
    >
      <div>
        <div
          class="container"
          v-for="(featureName, featureKey) in feature"
          :key="featureKey"
        >
          <div v-if="featureKey == 1">
            {{ featureName.description }}
          </div>
          <div
            class="container"
            v-for="(scenario, scenarioKey) in featureName.scenarios"
            :key="scenarioKey"
          >
            <div
              class="container"
              v-for="(scenarioName, scenarioNameKey) in scenario"
              :key="scenarioNameKey"
            >
              <div v-if="scenarioNameKey == 1">
                {{ scenarioName.description }}
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

<style></style>
