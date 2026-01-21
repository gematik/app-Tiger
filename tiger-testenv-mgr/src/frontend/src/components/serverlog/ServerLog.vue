<!--


    Copyright 2021-2025 gematik GmbH

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
<template>
  <div id="logs_pane" class="tab-pane execution-pane-tabs" role="tabpanel">
    <div class="ps-3 sticky-element">
      <table aria-label="Filter and search toolbar of the server log tab pane">
        <thead>
          <tr>
            <th>Filter servers:</th>
            <th class="ps-2">Filter by Text:</th>
            <th>Loglevel:</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>
              <div class="ps-0 pt-2 pb-4">
                <div
                  id="test-server-log-pane-buttons"
                  class="justify-content-between"
                  style="display: inline-block"
                >
                  <div
                    id="test-server-log-pane-server-all"
                    :class="[
                      'btn server-buttons',
                      isServerSelected(ALL) ? 'active' : '',
                    ]"
                    @click.prevent="toggleServer(ALL)"
                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="16"
                      height="16"
                      class="bi bi-circle-fill"
                      fill="currentColor"
                      viewBox="0 0 16 16"
                    >
                      <circle cx="8" cy="8" r="8" />
                    </svg>
                    Show all logs
                  </div>
                  <div
                    v-for="(serverName, logIndex) in logServers"
                    :key="logIndex"
                    style="display: inline-block"
                  >
                    <div
                      :id="`test-server-log-pane-server-${serverName}`"
                      :class="[
                        'btn server-buttons',
                        isServerSelected(serverName) ? 'active' : '',
                      ]"
                      @click.prevent="toggleServer(serverName)"
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="16"
                        height="16"
                        fill="currentColor"
                        class="bi bi-circle-fill"
                        viewBox="0 0 16 16"
                      >
                        <circle cx="8" cy="8" r="8" />
                      </svg>
                      {{ serverName }}
                    </div>
                  </div>
                </div>
              </div>
            </td>
            <td>
              <div class="container-fluid ps-2 pt-2 pb-4">
                <input
                  id="test-server-log-pane-input-text"
                  v-model="selectedText"
                  placeholder="filter text"
                />
              </div>
            </td>
            <td>
              <div class="container-fluid ps-0 pt-2 pb-4">
                <select
                  id="test-server-log-pane-select"
                  v-model="selectedLoglevel"
                  class="selectpicker"
                >
                  <option
                    v-for="(loglevelName, logIndex) in getLogLevel()"
                    :id="`test-server-log-pane-select-${loglevelName}`"
                    :key="logIndex"
                    :value="LogLevel[loglevelName]"
                  >
                    {{ loglevelName }}
                  </option>
                </select>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      v-for="(serverLog, logIndex) in filteredLogs(
        serverLogs,
        selectedServers,
        selectedText,
        selectedLoglevel,
      )"
      :key="logIndex"
      :class="`logList row type-${serverLog.logLevel.toLowerCase()} test-server-log-pane-log-row`"
    >
      <div class="col-1 test-server-log-pane-log-1">
        {{ serverLog.serverName }}
      </div>
      <div class="col-2 logDate test-server-log-pane-log-2">
        {{ getReadableTime(serverLog.localDateTime) }}
      </div>
      <div
        :class="`col-9 type-${serverLog.logLevel.toLowerCase()} test-server-log-pane-log-3`"
      >
        [{{ serverLog.logLevel.toUpperCase() }}]
        <span
          v-if="serverLog.logMessage"
          v-html="serverLog.logMessage.replaceAll('\n', '<br/>')"
        ></span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import TigerServerLogDto from "@/types/TigerServerLogDto";
import LogLevel from "@/types/LogLevel";
import { DateTimeFormatter, LocalDateTime } from "@js-joda/core";
import { ref } from "vue";

defineProps<{
  serverLogs: Array<TigerServerLogDto>;
  logServers: Array<string>;
}>();

const ALL: string = "__all__";
const selectedText = ref("");
const selectedLoglevel = ref(LogLevel.ALL.toString());
const selectedServers = ref<Set<string>>(new Set([ALL]));

const formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
  "MM/dd/yyyy HH:mm:ss.SSS",
);

function getReadableTime(localDateTime: LocalDateTime): string {
  if (localDateTime) {
    return localDateTime.format(formatter);
  }
  return "";
}

function isServerSelected(serverId: string): boolean {
  return selectedServers.value.has(serverId);
}

function toggleServer(serverId: string) {
  if (serverId === ALL) {
    selectedServers.value = new Set([ALL]);
  } else {
    selectedServers.value.delete(ALL);
    if (selectedServers.value.has(serverId)) {
      selectedServers.value.delete(serverId);
    } else {
      selectedServers.value.add(serverId);
    }
  }
  // check if all server were deactived -> automatically activate all-server-button
  if (selectedServers.value.size === 0) {
    toggleServer(ALL);
  }
}

function getLogLevel(): Array<string> {
  const logLevelValues = new Array<string>();
  const length = Object.keys(LogLevel).length / 2;
  for (let i = length - 1; i > -1; i--) {
    logLevelValues.push(LogLevel[i]);
  }
  return logLevelValues;
}

function filteredLogs(
  serverLogs: Array<TigerServerLogDto>,
  selectedServers: Set<string>,
  selectedText: string,
  selectedLoglevel: string,
) {
  if (
    selectedServers &&
    selectedServers.size > 0 &&
    !selectedServers.has(ALL)
  ) {
    return serverLogs.filter((log) => {
      return Array.from(selectedServers).some((selectedServer) => {
        const filteredLog = filterLogLevel(log, selectedText, selectedLoglevel);
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

function filterLogLevel(
  log: TigerServerLogDto,
  selectedText: string,
  selectedLoglevel: string,
): TigerServerLogDto | null {
  const logLevel: LogLevel = LogLevel[log.logLevel as keyof typeof LogLevel];
  if (logLevel <= parseInt(selectedLoglevel)) {
    if (selectedText) {
      if (
        log.logMessage?.indexOf(selectedText) !== -1 ||
        log.logLevel?.indexOf(selectedText) !== -1 ||
        getReadableTime(log.localDateTime).indexOf(selectedText) !== -1
      ) {
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
  background: rgba(200, 200, 200, 0.2);
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
  background: #fcfcfd;
  color: var(--bs-success);
  cursor: default;
}

.sticky-element {
  position: sticky;
  top: 0;
  background-color: #ffffff;
  padding: 10px;
}
</style>
