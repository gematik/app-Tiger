<template>
  <ul class="nav nav-tabs">
    <li class="nav-item nav-logo">
      <img class="navbar-brand right" src="img/tiger-mono-64.png" width="48"/> Tiger Workflow UI
    </li>
    <li class="navbar-divider">&nbsp;</li>
    <li class="nav-item">
      <router-link to="/" class="nav-link">Test run</router-link>
    </li>
    <li class="nav-item">
      <router-link to="/about" class="nav-link">Contact</router-link>
    </li>
  </ul>
  <router-view></router-view>
</template>

<script lang="ts">
import {defineComponent} from 'vue';
import "bootstrap";

export default defineComponent({
  name: 'App',
});
</script>

<style lang="css">
.left, i.fa.left, i.far.left, i.fas.left, i.fa-sold.left {
  padding-right: 0.5rem;
}

.right, i.fa.right, i.far.right, i.fas.right, i.fa-sold.right {
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
</style>
