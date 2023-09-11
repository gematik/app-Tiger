<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<script setup lang="ts">
import {ICellRendererParams} from "ag-grid-community";


function mapSourceToIcon(sourceName : string){
  switch (sourceName) {
    case "DEFAULTS":  return "fa-cog";
    case "MAIN_YAML":  return "fa-file";
    case "HOST_YAML":  return "fa-file";
    case "ADDITIONAL_YAML":  return "fa-file";
    case "TEST_YAML":  return "fa-file";
    case "ENV":  return "fa-server";
    case "PROPERTIES":  return "fa-gears";
    case "CLI":  return "fa-termina";
    case "RUNTIME_EXPORT":  return "fa-cloud";
    case "TEST_CONTEXT":  return "fa-code";
    case "THREAD_CONTEXT":  return "fa-thread";
    case "SCOPE_LOCAL_CONTEXT": return  "fa-thread";
    default: return "fa-question"
  }
}

defineProps<{
  params: ICellRendererParams
}>()
</script>

<template>
<i class="fa fa-lg" :title="params.value" :class="mapSourceToIcon(params.value)"/>
</template>
