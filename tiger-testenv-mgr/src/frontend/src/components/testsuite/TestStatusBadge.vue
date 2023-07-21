<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <span :class="`${highlightText ? 'statustext' : ''}`" >
    <i :class="`statusbadge ${testStatus.toLowerCase()} left ${getTestResultIcon(testStatus, 'solid')}`"
       :title="`${testStatus}`"
       :id="`${link}`" ></i>
    {{ text }}
  </span>
  <span :class="`statusbadge ${getStatusFGAndBGColorClass(testStatus)} badge rounded-pill`">
      {{ testStatus }}
    </span>
</template>

<script setup lang="ts">

import {getTestResultIcon} from "@/types/testsuite/TestResult";

defineProps<{
  testStatus: string
  highlightText: boolean
  text: string
  link: string
}>();

function getStatusFGAndBGColorClass(status: string): string {
  return status.toLowerCase() + ' bg-' + status.toLowerCase() + 'test-status-'+ status.toLowerCase();
}

</script>

<style scoped>
.statusbadge.badge {
  font-size: 50%;
  vertical-align: top;
}

i.statusbadge {
  font-size: 1.25rem;
  padding-right: 5rem;
}

.statustext {
  background: var(--gem-primary-025);
  border-radius: 1rem;
  padding: 0.25rem 1rem;
  margin-right: 0.5rem;
}
</style>
