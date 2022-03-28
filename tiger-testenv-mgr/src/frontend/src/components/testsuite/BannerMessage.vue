<template>
  <div>
    <div
        class="container"
        v-for="(banner, index) in bannerData"
        :key="'m-' + index"
    >
      {{ banner.text }}
    </div>
  </div>
</template>

<script setup lang="ts">
import MessageUpdateDto from "@/types/MessageUpdateDto";

defineProps<{
  bannerData: MessageUpdateDto[]
}>()
</script>

<style></style>
