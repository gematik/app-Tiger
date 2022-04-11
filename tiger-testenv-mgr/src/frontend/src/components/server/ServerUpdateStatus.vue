<template>
  <div>
    {{ serverUpdateStatus }}

  </div>
</template>

<script setup lang="ts">
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";

defineProps<{
  serverUpdateStatus: Map<string, TigerServerStatusUpdateDto>
}>()
</script>

<style scoped></style>
