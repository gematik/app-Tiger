<template>
  <ul class="nav nav-tabs">
    <li class="nav-item">
      <router-link to="/" class="nav-link">Testprojekt</router-link>
    </li>
    <li class="nav-item">
      <router-link to="/about" class="nav-link">Kontakt</router-link>
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
@import "bootstrap/dist/css/bootstrap.css";

i.fa.left, i.far.left, i.fas.left {
  padding-right: 0.5rem;
}

i.fa.right, i.far.right, i.fas.right {
  padding-left: 0.5rem;
}

.truncate-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style lang="scss">
/*
$fa-font-path: "~@fortawesome/fontawesome-free/webfonts/";
@import "~@fortawesome/fontawesome-free/scss/fontawesome.scss";
@import "~@fortawesome/fontawesome-free/scss/core.scss";
@import "~@fortawesome/fontawesome-free/scss/regular.scss"; // far
@import "~@fortawesome/fontawesome-free/scss/solid.scss"; // fas
@import "~@fortawesome/fontawesome-free/scss/brands.scss"; // fab<
 */
</style>
