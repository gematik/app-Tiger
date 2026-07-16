<!--


    Copyright 2021-2026 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    *******

    For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.

-->
<script setup lang="ts">
import FileUploader from "./components/FileUploader.vue";
import TopologyDiagram from "./components/TopologyDiagram.vue";
</script>

<template>
  <div class="app-layout">
    <FileUploader />
    <div class="topology-container">
      <TopologyDiagram :show-filtering="false" />
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100dvh;
}

.topology-container {
  flex: 1;
  min-height: 0;
}
</style>
