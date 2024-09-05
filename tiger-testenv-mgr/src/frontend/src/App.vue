<!--
  - Copyright 2024 gematik GmbH
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <TestProject/>
</template>

<script lang="ts">
import {defineComponent} from 'vue';
import TestProject from "@/pages/TestProject.vue";
import "bootstrap";

export default defineComponent({
  name: 'App',
  components: {
    TestProject
  }
});
</script>

<style lang="css">
.left, i.fa.left, i.far.left, i.fas.left, i.fa-solid.left {
  margin-right: 0.5rem;
  padding-right: 0;
}

.right, i.fa.right, i.far.right, i.fas.right, i.fa-solid.right {
  padding-left: 0.5rem;
}

.truncate-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-logo {
  background: whitesmoke;
  padding-right: 1rem;
}

.passed {
  color: var(--gem-success-300) !important;
}

.test_discovered {
  color: var(--gem-primary-300) !important;
}

.skipped {
  color: var(--gem-warning-400) !important;
}

.pending, .unused {
  color: var(--gem-neutral-400) !important;
}

.undefined, .ambiguous {
  color: orchid !important;
}

.failed {
  color: var(--gem-error-400) !important;
}

.bg-passed {
  background: var(--gem-success-050) !important;
}

.bg-test_discovered {
  background: var(--gem-primary-050) !important;
}

.bg-skipped {
  background: var(--gem-warning-100) !important;
}

.bg-pending, .bg-unused {
  background: var(--gem-neutral-100) !important;
}

.bg-undefined, .bg-ambiguous {
  background: #ffccff !important;
}

.bg-failed {
  background: var(--gem-error-100) !important;
}

.smallcode {
  font-size: 60%;
  font-family: courier, monospace;
  color: darkgray;
}
</style>
