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
  <span :class="`${highlightText ? 'statustext' : ''}`">
    <a
      v-if="testStatus === 'FAILED' && failureLink"
      class="failureLink"
      :href="failureLink"
    >
      <i
        :id="link"
        :class="`statusbadge ${testStatus.toLowerCase()} left ${getTestResultIcon(testStatus, 'solid')}`"
        :title="`${statusMessage ? statusMessage : testStatus}`"
      ></i>
    </a>
    <i
      v-else
      :id="link"
      :class="`statusbadge ${testStatus.toLowerCase()} left ${getTestResultIcon(testStatus, 'solid')}`"
      :title="`${statusMessage ? statusMessage : testStatus}`"
    ></i>
    <a class="scenarioLink" :href="`#${link}`">
      {{ text }}
    </a>
  </span>
  <span
    :class="`statusbadge ${getStatusFGAndBGColorClass(testStatus)} badge rounded-pill test-feature-status-word`"
  >
    {{ testStatus }}
  </span>
</template>

<script setup lang="ts">
import { getTestResultIcon } from "@/types/testsuite/TestResult";

defineProps<{
  testStatus: string;
  statusMessage: string;
  highlightText: boolean;
  text: string;
  link: string;
  failureLink?: string;
}>();

function getStatusFGAndBGColorClass(status: string): string {
  return `${status.toLowerCase()} bg-${status.toLowerCase()} test-status-${status.toLowerCase()}`;
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

.scenarioLink {
  text-decoration: none;
  color: var(--gem-primary-400);
}
</style>
