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

export default defineComponent({
  name: 'App',
});
</script>

<style>

</style>
