<template>
  <div>
    <div
      class="container"
      v-for="(step, index) in stepData"
      :key="index"
      :style="`color: ${colorFont(step)}`"
      >
      {{ step.step }} - {{ step.status }}
    </div>
  </div>
</template>

<script setup lang="ts">
import Step from "@/types/Step";
import DataType from "@/types/DataType";
import TestResult from "@/types/TestResult";

defineProps<{
  stepData: Step[];
}>();

 function colorFont(step: Step): string {
  let bold: string = "";
  let color: string = "black";

  if (step.type === DataType.FEATURE) {
    bold = ";font-weight: bold";
  }
  if (step.type === DataType.SCENARIO) {
     bold = ";font-style: italic";
  }
  switch (step.status) {
    case TestResult.PASSED:
      color = "GREEN";
      break;
    case TestResult.SKIPPED:
    case TestResult.PENDING:
      color = "ORANGE";
      break;
    case TestResult.UNDEFINED:
    case TestResult.AMBIGUOUS:
    case TestResult.UNUSED:
      color = "GREY";
      break;
    case TestResult.FAILED:
      color = "RED";
      break;
  }
  return color + bold;
} 
</script>

<style></style>
