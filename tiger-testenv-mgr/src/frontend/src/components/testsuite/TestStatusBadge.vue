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
