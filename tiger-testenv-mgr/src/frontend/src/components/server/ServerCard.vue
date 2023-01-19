<template>
  <div class="serverbox">
    <div class="alert-heading server-name truncate-text">
      <i :class="`${getServerIcon(server.type)} left serverstatus-${server.status.toLowerCase()}`" :title="`${server.status}`"></i>
      <span>{{ server.name }}</span> <small>({{ server.status }})</small>
    </div>
    <div class="serverurl truncate-text" v-if="server.baseUrl">
      {{ server.baseUrl }}
      <a :href="server.baseUrl" :target="server.name">
        <i class="fa-solid fa-up-right-from-square" alt="pop out server"></i>
      </a>
    </div>
    <span v-else></span>
    <div class="serverstatus">
      <a class="p-1 btn btn-sm btn-serverstatus-history" data-bs-toggle="collapse" :href="`#history_${server.name}`" role="button"
         aria-expanded="false"
         :aria-controls="`history_${server.name}`">
        {{ server.statusMessage }}
        <i class="fa-solid fa-angles-down right"></i>
      </a>
      <div class="server-history collapse text-primary p-2 pb-0" :id="`history_${server.name}`">
        <div v-for="(serverstatus,index) in server.statusUpdates" class="pl-3 pr-3 pb-2" :key="index">
          {{ serverstatus }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import TigerServerStatusDto from "@/types/TigerServerStatusDto";

defineProps<{
  server: TigerServerStatusDto;
}>();

function getServerIcon(type: string): string {
  switch (type) {
    case 'docker':
      return "fab fa-docker";
    case 'compose':
      return "fas fa-cubes";
    case 'tigerProxy':
      return "fas fa-project-diagram";
    case 'local_tiger_proxy':
      return "fas fa-project-diagram";
    case 'externalJar':
      return "fas fa-rocket";
    case 'externalUrl':
      return "fas fa-external-link-alt";
    case 'helmChart':
        return 'fas fa-network-wired';
    default:
      return "far fa-question";
  }
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

.serverurl a {
  color: inherit;
  padding-left: 0.25rem;
}

.serverstatus {
  font-size: 75%;
  font-style: italic;
  border: 1px solid white;
}

.server-history {
  background: whitesmoke;
  border: 1px solid lightgray;
}

.btn-serverstatus-history {
  width: 100%;
  text-align: center;
  text-decoration: none;
  box-shadow: none;
  background: whitesmoke;
  border: 1px solid lightgray;
}

</style>
