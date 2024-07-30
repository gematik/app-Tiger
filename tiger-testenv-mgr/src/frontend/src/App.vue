<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
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
