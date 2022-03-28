<template>
  <div>
    <h2> Test </h2>
    <div v-for="(server, index) in serverStatusData?.servers" :key="index"> {{ index }} - {{ server.type }} -
      {{ server.status }} - {{ showLastStatus(server.statusUpdates) }}
      <button class="btn" data-toggle="collapse" data-target="#serverStatusUpdate">All Server Status</button>
      <div id="serverStatusUpdate" class="collapse">
        <ul v-for="(serverstatus, i) in server.statusUpdates" :key="i">
          <li> {{ serverstatus }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import TigerEnvStatusDto from "@/types/TigerEnvStatusDto";

defineProps<{
  serverStatusData: TigerEnvStatusDto
}>()

function showLastStatus(array: Array<string>): string {
  return array[array.length - 1];
}

</script>

<style scoped></style>
