<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div id="workflow-messages" :class="`alert banner-message fade ${bannerData.length > 0 ? 'show' : ''}`" role="alert">
    <i class="btn-banner-close fa-solid fa-xmark" v-on:click="closeWindow"></i>
    <h4 class="pt-3 pb-0 text-center">
      <i class="fa-solid fa-bullhorn fa-flip-horizontal"></i>
      Workflow message
    </h4>
    <div v-if="bannerData.length > 0" :style="`color: ${bannerData[bannerData.length-1].color};`" class="banner">
      {{ bannerData[bannerData.length - 1].text }}
    </div>
  </div>
</template>

<script setup lang="ts">
import BannerMessage from "@/types/BannerMessage";
import {onUpdated} from "vue";

onUpdated(() => {
  document.getElementById('workflow-messages')!.classList.toggle('show', true);
});

defineProps<{
  bannerData: BannerMessage[];
}>();

function closeWindow(ev : MouseEvent) {
  document.getElementById('workflow-messages')!.classList.toggle('show');
}
</script>

<style scoped>
#workflow-messages {
  position: fixed;
  bottom: 0;
  margin: 0;
  width: 50%;
  z-index: 20000;
  padding: 0.5rem;
  background: rgba(234, 236, 245, 0.9);
  border: 1px solid #717BBC;
  color: #717BBC;
  transition: opacity 0.75s ease-in-out !important;
}

.banner {
  font-weight: bolder;
  font-size: 150%;
  margin-bottom: 0;
  text-align: center;
  padding: 0.5rem;
}

.btn-banner-close {
  color: #717BBC;
  border-radius: 0.5rem;
  padding: 0.75rem;
  background: #FCFCFD;
  float: right;
  cursor: pointer;
}
</style>
