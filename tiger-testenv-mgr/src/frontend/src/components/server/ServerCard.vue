<template>
  <div :class="`alert alert-${colorStatus(server.status)} serverbox`">
    <div class="alert-heading server-name truncate-text">
      <i :class="`${getServerIcon(server.type)} left`"></i>
      <span>{{ server.name }} ({{ server.status }})</span>
    </div>
    <div class="serverurl truncate-text" v-if="server.baseUrl">{{ server.baseUrl }}</div>
    <span v-else></span>
    <div class="serverstatus">
      <a class="p-1" data-bs-toggle="collapse" :href="`#${getHistoryCollapseId(server)}`" role="button" aria-expanded="false"
         :aria-controls="`${getHistoryCollapseId(server)}`"><i class="fa-solid fa-circle-chevron-down left"></i> {{ server.statusMessage }}</a>
      <div class="collapse bg-white p-2 pb-0" :id="`${getHistoryCollapseId(server)}`">
        <div v-for="(serverstatus) in server.statusUpdates" class="pl-3 pr-3 pb-2">
          {{ serverstatus }}
        </div>
      </div>
    </div>
  </div>
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

function getServerIcon(type: string): string {
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
