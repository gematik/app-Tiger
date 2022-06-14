<template>
  <div class="tab-pane execution-pane-tabs" id="logs_pane" role="tabpanel">
    <div class="ps-3">
      Filter servers:
      <div class="container-fluid pt-2 pb-4">
        <select class="form-select" multiple v-model="selectedServers" size="3">
          <option value="__all__" selected>Show all logs</option>
          <option v-for="(serverName,logIndex) in logServers" :key="logIndex">
            {{ serverName }}
          </option>
        </select>
      </div>
    </div>

    <div :class="`logList row type-${serverLog.logLevel.toLowerCase()}`"
        v-for="(serverLog, logIndex) in filteredLogs(serverLogs, selectedServers)" :key="logIndex">
      <div class="col-1">{{serverLog.serverName}}</div>
      <div class="col-2 logDate">{{ getReadableTime(serverLog.localDateTime) }}</div>
      <div :class="`col-9 type-${serverLog.logLevel.toLowerCase()}`">
        [{{serverLog.logLevel.toUpperCase()}}]
        <span v-html="serverLog.logMessage.replaceAll('\n', '<br/>')"></span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">

import TigerServerLogDto from "@/types/TigerServerLogDto";
import {DateTimeFormatter, LocalDateTime} from "@js-joda/core";

defineProps<{
  serverLogs: Array<TigerServerLogDto>;
  logServers: Array<string>;
  selectedServers: Array<string>;
}>();

const formatter: DateTimeFormatter = DateTimeFormatter.ofPattern('MM/dd/yyyy HH:mm:ss.SSS');

function getReadableTime(localDateTime: LocalDateTime): string {
  if (localDateTime) {
    return localDateTime.format(formatter);
  }
  return "";
}

function filteredLogs(serverLogs: Array<TigerServerLogDto>, selectedServers: Array<string>) {
  if (selectedServers && selectedServers.length > 0 && selectedServers.indexOf("__all__") === -1) {
    return serverLogs.filter((log) => {
      return selectedServers.some((selectedServer) => {
        return ((selectedServer.indexOf(log.serverName as string) !== -1));
      });
    });
  } else {
    return serverLogs;
  }
}
</script>

<style scoped>
.logList {
  font-size: 80%;
}
.logList:nth-child(odd) {
  background: rgba(200,200,200,0.2);
}
.logDate {
  white-space: nowrap;
  overflow-x: auto;
  padding: 0 2rem;
}

.type-info {
  color: #333;
}

.type-error {
  color: darkred;
  font-weight: bold;
}

.type-warn {
  color: darkorange;
  font-weight: bold;
}
</style>
