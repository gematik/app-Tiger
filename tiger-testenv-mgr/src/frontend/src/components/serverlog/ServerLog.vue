<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div class="tab-pane execution-pane-tabs" id="logs_pane" role="tabpanel">
    <div class="ps-3">
      <table>
        <thead>
        <tr>
          <th>Filter servers:</th>
          <th class="ps-2">Text:</th>
          <th>Loglevel:</th>
        </tr>
        </thead>
        <tbody>
        <tr>
          <td>
            <div class="ps-0 pt-2 pb-4">
            <div class="justify-content-between" style="display: inline-block;" >
              <div class="btn active server-buttons" @click="setServer(selectedServers, '__all__', $event)">Show all logs</div>
              <div v-for="(serverName,logIndex) in logServers" :key="logIndex" style="display: inline-block;">
                <div class="btn server-buttons" @click="setServer(selectedServers, serverName, $event)">{{ serverName }}</div>
              </div>
            </div>
          </div>
          </td>
          <td>
            <div class="container-fluid ps-2 pt-2 pb-4">
              <input v-model="selectedText" placeholder="add text" />
            </div>
          </td>
          <td>
            <div class="container-fluid ps-0 pt-2 pb-4">
              <select class="selectpicker" v-model="selectedLoglevel">
                <option v-for="(loglevelName,logIndex) in getLogLevel()" :key="logIndex" :value="LogLevel[loglevelName]">
                  {{loglevelName}}
                </option>
              </select>
            </div>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div :class="`logList row type-${serverLog.logLevel.toLowerCase()}`"
        v-for="(serverLog, logIndex) in filteredLogs(serverLogs, selectedServers, selectedText, selectedLoglevel)" :key="logIndex">
      <div class="col-1">{{serverLog.serverName}}</div>
      <div class="col-2 logDate">{{ getReadableTime(serverLog.localDateTime) }}</div>
      <div :class="`col-9 type-${serverLog.logLevel.toLowerCase()}`">
        [{{serverLog.logLevel.toUpperCase()}}]
        <div v-if="serverLog.logMessage">
          <span v-html="serverLog.logMessage.replaceAll('\n', '<br/>')"></span>
        </div>
      </div>
    </div>
  </div>

</template>

<script setup lang="ts">

import TigerServerLogDto from "@/types/TigerServerLogDto";
import LogLevel from "@/types/LogLevel";
import {DateTimeFormatter, LocalDateTime} from "@js-joda/core";
import {ref} from "vue";

const props = defineProps<{
  serverLogs: Array<TigerServerLogDto>;
  logServers: Array<string>;
  selectedServers: Array<string>;
  selectedLoglevel: string;
  selectedText: string;
}>();

const selectedText = ref(props.selectedText);
const selectedLoglevel = ref(props.selectedLoglevel);

const formatter: DateTimeFormatter = DateTimeFormatter.ofPattern('MM/dd/yyyy HH:mm:ss.SSS');

function getReadableTime(localDateTime: LocalDateTime): string {
  if (localDateTime) {
    return localDateTime.format(formatter);
  }
  return "";
}

const ALL :string = "__all__";

function setServer(selectedServers: Array<string>, serverId: string, event: MouseEvent) {
  event.preventDefault();
  const buttons = document.getElementsByClassName('server-buttons');
  if (serverId === ALL) {
    for (let button of buttons) {
      button.classList.toggle("active", false);
    }
    if (selectedServers.length > 0) {
      selectedServers.splice(0, selectedServers.length);
    }
  }
  for (let button of buttons) {
    if (button.textContent === "Show all logs") {
      button.classList.toggle("active", false);
      const index = selectedServers.findIndex((server) => server === ALL);
      if ( index > -1) {
        selectedServers.splice(index, 1);
      }
    }
  }
  if (selectedServers.includes(serverId)) {
    const index = selectedServers.findIndex((server) => server === serverId);
    selectedServers.splice(index, 1);
    (event.target as HTMLElement)?.classList?.toggle("active", false);
    // check if all server were deactived -> automatically activate all-server-button
    if (selectedServers.length == 0) {
      selectedServers.push(ALL);
      buttons[0].classList?.toggle("active", true);
    }
  } else {
    selectedServers.push(serverId);
    (event.target as HTMLElement)?.classList?.toggle("active", true);
  }
}

function getLogLevel(): Array<string> {
  let logLevelValues = new Array<string>();
  const length = Object.keys(LogLevel).length / 2;
  for (let i = length - 1; i > -1; i--) {
    logLevelValues.push(LogLevel[i]);
  }
  return logLevelValues;
}

function filteredLogs(serverLogs: Array<TigerServerLogDto>, selectedServers: Array<string>, selectedText: string, selectedLoglevel: string) {
  if (selectedServers && selectedServers.length > 0 && selectedServers.indexOf(ALL) === -1) {
    return serverLogs.filter((log) => {
      return selectedServers.some((selectedServer) => {
        let filteredLog = filterLogLevel(log, selectedText, selectedLoglevel)
        if (selectedServer === (log.serverName as string)) {
          return filteredLog;
        }
      });
    });
  } else {
    return serverLogs.filter((log) => {
      return filterLogLevel(log, selectedText, selectedLoglevel);
    });
  }
}

function filterLogLevel(log: TigerServerLogDto, selectedText: string, selectedLoglevel: string) : TigerServerLogDto|null {
  const logLevel: LogLevel = LogLevel[log.logLevel as keyof typeof LogLevel];
  if (logLevel <= parseInt(selectedLoglevel)) {
    if (selectedText) {
      if ((log.logMessage?.indexOf(selectedText) !== -1) ||
          (log.logLevel?.indexOf(selectedText) !== -1) ||
          (getReadableTime(log.localDateTime).indexOf(selectedText) !== -1)) {
        return log;
      }
    } else return log;
  }
  return null;
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

.server-buttons {
  border: 1px solid var(--gem-primary-100);
  border-radius: 1rem;
  color: var(--gem-primary-300);
  margin-right: 0.5em;
  margin-top: 0.3em;
  display: inline-block;
}
.server-buttons.active {
  background: #FCFCFD;
  color: var(--bs-primary);
  cursor: default;
}
</style>
