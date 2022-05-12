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
  padding-right: 0.5rem;
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
  color: forestgreen !important;
}

.skipped {
  color: darkorange !important;
}

.pending, .unused {
  color: lightslategray !important;
}

.undefined, .ambiguous {
  color: orchid !important;
}

.failed {
  color: orangered !important;
}

</style>
