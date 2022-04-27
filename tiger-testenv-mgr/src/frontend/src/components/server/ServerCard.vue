<template>
  <div :class="`alert alert-${colorStatus(server.status)} serverbox`">
    <div class="alert-heading server-name truncate-text">
      <i :class="`${getServerIcon(server.type)} left`"></i>
      <span>{{ server.name }} ({{ server.status }})</span>
    </div>
    <div class="serverurl truncate-text" v-if="server.baseUrl">{{ server.baseUrl }}</div>
    <span v-else></span>
    <div class="serverstatus">
      <a data-bs-toggle="collapse" :href="`#${getHistoryCollapseId(server)}`" role="button" aria-expanded="false"
         :aria-controls="`${getHistoryCollapseId(server)}`">{{ server.statusMessage }}</a>
      <div class="collapse" :id="`${getHistoryCollapseId(server)}`">
        <div class="card card-body">
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
/*
import {library} from '@fortawesome/fontawesome-svg-core'
import {faUserSecret} from '@fortawesome/free-solid-svg-icons'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'

library.add(faUserSecret)
*/

let isActive = ref(false);

defineProps<{
  server: TigerServerStatusDto;
}>();

const serverIcons = {
  docker: "fab fa-docker",
  compose: "fas fa-cubes",
  tigerProxy: "fas fa-project-diagram",
  local_tiger_proxy: "fas fa-project-diagram",
  externalJar: "fas fa-rocket",
  externalUrl: "fas fa-external-link-alt"
}

function getServerIcon(type: string) : string {
  if (type) {
    return serverIcons[type];
  } else {
    return "far fa-question";
  }
}

function colorStatus(status: TigerServerStatus): string {
  switch (status) {
    case TigerServerStatus.NEW:
      return "secondary";
    case TigerServerStatus.STARTING:
      return "info";
    case TigerServerStatus.RUNNING:
      return "success";
    case TigerServerStatus.STOPPED:
      return "error";
    default:
      return "secondary";
  }
}

function getHistoryCollapseId(server: TigerServerStatusDto): string {
   return "history_" + server.name;
}

function toggleStatusList() {
  isActive.value = !isActive.value;
}
</script>

<style scoped>

.serverbox {
  padding: 0.25rem;
}

.serverbox > div {
  margin-top: 0.5rem;
}

.server-name {
  margin-top: 0.5rem;
}

.server-name > span {
  font-weight: bold;
}

.serverurl {
  font-size: 75%;
  margin-left: 1rem;
}

.serverstatus {
  font-size: 75%;
  font-style: italic;
  font-color: inherit;
  border: 1px solid white;
}

.serverstatus > a {
  display: block;
  width: 100%;
  text-align: center;
  text-decoration: none;
  color: inherit;
}

</style>
