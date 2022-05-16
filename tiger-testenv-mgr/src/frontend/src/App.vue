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
  color: greenyellow !important;
}

.skipped {
  color: lightsalmon !important;
}

.pending, .unused {
  color: #bbbbbb !important;
}

.undefined, .ambiguous {
  color: orchid !important;
}

.failed {
  color: orangered !important;
}

.bg-passed {
  background: #ECFDF3 !important;
}

.bg-skipped {
  background: #FEFDD6 !important;
}

.bg-pending, .bg-unused {
  background: #dddddd !important;
}

.bg-undefined, .bg-ambiguous {
  background: #ffccff !important;
}

.bg-failed {
  background: #FECDD6 !important;
}
</style>
