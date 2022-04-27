<template>
  <div class="container">
    <div>Server status</div>
    <div
      v-for="(server, serverindex) in serverStatusData?.servers"
      :key="serverindex"
    >
      <ServerCard :server="server[1]" />
    </div>
  </div>
</template>

<script setup lang="ts">
import TigerEnvStatusDto from "@/types/TigerEnvStatusDto";
import ServerCard from "@/components/server/ServerCard.vue";

defineProps<{
  serverStatusData: TigerEnvStatusDto;
}>();
</script>

<style scoped></style>
