<template>
  <div class="serverbox">
    <div :class="`alert-heading server-name truncate-text serverstatus-${server.status.toLowerCase()}`">
      <i :class="`${getServerIcon(server.type)} left`"></i>
      <span>{{ server.name }} ({{ server.status }})</span>
    </div>
    <div class="serverurl truncate-text" v-if="server.baseUrl">{{ server.baseUrl }}</div>
    <span v-else></span>
    <div class="serverstatus">
      <a class="p-1" data-bs-toggle="collapse" :href="`#${getHistoryCollapseId(server)}`" role="button" aria-expanded="false"
         :aria-controls="`${getHistoryCollapseId(server)}`"><i class="fa-solid fa-circle-chevron-down left"></i> {{ server.statusMessage }}</a>
      <div class="collapse bg-white text-primary p-2 pb-0" :id="`${getHistoryCollapseId(server)}`">
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
    // @ts-ignore
    return serverIcons[type];
  } else {
    return "far fa-question";
  }
}

function getHistoryCollapseId(server: TigerServerStatusDto): string {
  return "history_" + server.name;
}

</script>

<style scoped>

/*noinspection CssUnusedSymbol*/
.serverbox {
  padding: 0.5rem;
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
