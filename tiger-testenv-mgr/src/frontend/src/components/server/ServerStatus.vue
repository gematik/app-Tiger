<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div class="container">
    <div v-if="serverStatusData.size === 0" class="alert alert-light" style="height:200px;width:100%;">
      <i class="fa-solid fa-spinner left"></i> Waiting for updates...
    </div>
    <div v-else class="alert alert-light">
      <div v-for="server in sortedServerList(serverStatusData)">
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

<style scoped></style>
