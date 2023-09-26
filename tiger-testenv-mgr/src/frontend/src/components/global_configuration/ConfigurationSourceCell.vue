<!--
  - Copyright (c) 2023 gematik GmbH
  - 
  - Licensed under the Apache License, Version 2.0 (the License);
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  - 
  -     http://www.apache.org/licenses/LICENSE-2.0
  - 
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an 'AS IS' BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
