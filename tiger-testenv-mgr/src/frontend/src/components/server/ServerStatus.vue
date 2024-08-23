<!--
  - Copyright 2024 gematik GmbH
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="container">
    <div v-if="serverStatusData.size === 0" class="alert alert-light engraved w-100 p-3">
      <i class="fa-solid fa-spinner fa-spin left"></i> Waiting for updates...
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
