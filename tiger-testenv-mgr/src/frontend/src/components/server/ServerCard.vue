<template>
  <div class="container">
    <div class="card" style="margin-top: 10px">
      <div class="card-body">
        <!-- draw an colored icon -->
        <div id="container">
          <div class="wrapper">
            <i
              class="bi bi-circle-fill"
              :style="`font-size: 24px; color: ${colorStatus(server.status)}`"
            />
            <h4 class="card-title servername">{{ server.name }}</h4>
          </div>
          <div class="wrapper">
            <div></div>
            <p class="card-text hostname"> {{ server.baseUrl }}</p>
          </div>
          <div class="wrapper">
            <div></div>
            <p class="card-text hostname">Server: {{ server.status }}</p>
          </div>
          <div class="wrapper">
            <button
              type="button"
              class="btn btn-light"
              style="
                display: contents;
                background-color: white;
                border: 2px solid #555555;
                padding: 16px;
              "
              @click="toggleStatusList()"
            >
              <i class="bi bi-arrow-down" style="font-size: 24px"></i>
            </button>
            <p class="card-text servername">Letzter Status: {{ server.statusMessage }}</p>
          </div>
          <div v-if="isActive">
            <h5 style="margin-top: 5px">Kompletter Serverstatus:</h5>
            <ul>
              <li
                v-for="(serverstatus, servername) in server.statusUpdates"
                :key="servername"
              >
                {{ serverstatus }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!--     <div v-for="(server, index) in serverStatusData?.servers" :key="index"> {{ index }} - {{ server.type }} -
      {{ server.status }} - {{ showLastStatus(server.statusUpdates) }}
      <button class="btn" data-toggle="collapse" data-target="#serverStatusUpdate">All Server Status</button>
      <div id="serverStatusUpdate" class="collapse">
        <ul v-for="(serverstatus, i) in server.statusUpdates" :key="i">
          <li> {{ serverstatus }}</li>
        </ul>
      </div>
    </div> -->
</template>

<script setup lang="ts">
import TigerServerStatusDto from "@/types/TigerServerStatusDto";
import TigerServerStatus from "@/types/TigerServerStatus";
import {ref} from "vue";

let isActive = ref(false);

defineProps<{
  server: TigerServerStatusDto;
}>();

function showLastStatus(array: Array<string>): string {
  return array[array.length - 1];
}

function colorStatus(status: TigerServerStatus): string {
  switch (status) {
    case TigerServerStatus.NEW:
      return "Blue";
    case TigerServerStatus.STARTING:
      return "Yellow";
    case TigerServerStatus.RUNNING:
      return "Green";
    case TigerServerStatus.STOPPED:
      return "Red";
    default:
      return "White";
  }
}

function toggleStatusList() {
  isActive.value = !isActive.value;
}
</script>

<style scoped>
.wrapper {
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  align-items: center;
}

.servername {
  margin-top: 0.5rem;
  margin-left: 2rem;
}

.hostname {
  margin-left: 4rem;
}
</style>
