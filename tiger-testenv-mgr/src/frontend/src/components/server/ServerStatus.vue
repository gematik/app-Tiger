<template>
  <div class="container">
    <div>Server status</div>
    <div v-if="serverStatusData.size == 0" class="alert alert-danger" style="height:200px;width:100%;">
      <i class="fa-regular fa-hourglass left"></i> Waiting for updates...
    </div>
    <div v-else v-for="(server) in serverStatusData">
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
