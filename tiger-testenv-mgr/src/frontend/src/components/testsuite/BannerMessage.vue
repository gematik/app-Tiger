<template>
  <div>
    <div
        class="container"
        v-for="(banner, index) in bannerData"
        :key="'m-' + index"
      	:style="`color: ${colorFont(banner)}`"
    >
      {{ banner.text }}
    </div>
  </div>
</template>

<script setup lang="ts">
import BannerMessages from "@/types/BannerMessages";

defineProps<{
  bannerData: BannerMessages[]
}>()

function colorFont(banner: BannerMessages): string {
  let bold: string = "";
  
  if (banner.text.startsWith("Feature:")) {
     bold = ";font-weight: bold";
  }
  console.log(banner.farbe + bold);
  return banner.farbe + bold;
}

</script>

<style></style>
