<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div class="container">
    <div v-if="serverStatusData.size === 0" class="alert alert-light engraved w-100 p-3">
      <i class="fa-solid fa-spinner left"></i> Waiting for updates...
    </div>
    <div v-else class="server-status-box alert alert-light engraved" id="test-sidebar-server-status-box">
      <div v-for="(server, index) in sortedServerList(serverStatusData)" :key="index">
        <ServerCard :server="server"/>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ServerCard from "@/components/server/ServerCard.vue";
import TigerServerStatusDto from "@/types/TigerServerStatusDto";
import {sortedServerList} from "@/types/TigerServerStatus";

defineProps<{
  serverStatusData: Map<string, TigerServerStatusDto>;
}>();
</script>

<style scoped>
.server-status-box {
    color: var(--gem-primary-400);
}
</style>
