<!--
  - Copyright (c) 2022 gematik GmbH
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

<!--suppress CssUnusedSymbol -->
<template>
  <div>
    <div class="row">

      <!-- side bar -->
      <div class="sidebar-collapsed" id="sidebar-left">
        <!-- side bar title -->
        <h3 class="sidebar-title">
          <img v-on:click="Ui.toggleLeftSideBar(1)"
               class="navbar-brand right" src="img/tiger-mono-64.png" width="36" alt="Tiger logo"/>
          <span>Workflow UI</span>
          <i v-on:click="Ui.toggleLeftSideBar(0)"
             class="fa-solid fa-angles-left resizer-left-icon">
          </i>
        </h3>
        <!-- test run status -->
        <h4>
          <i v-on:click="Ui.toggleLeftSideBar(1)"
             :class="`${currentOverallTestRunStatus(featureUpdateMap)} fa-solid fa-square-poll-vertical left`">
          </i>
          <span>Status</span>
        </h4>
        <TestStatus :featureUpdateMap="featureUpdateMap" :started="started"/>
        <!-- feature list -->
        <h4>
          <i v-on:click="Ui.toggleLeftSideBar(1)"
             class="fa-solid fa-address-card left">
          </i>
          <span>Features</span>
        </h4>
        <FeatureList :featureUpdateMap="featureUpdateMap"/>
        <!-- server status -->
        <h4>
          <i v-on:click="Ui.toggleLeftSideBar(1)"
             :class="`serverstatus-${currentOverallServerStatus(currentServerStatus)} fa-solid fa-server left`">
          </i>
          <span>Servers</span>
        </h4>
        <ServerStatus :serverStatusData="currentServerStatus"/>
        <div class="container">
          <div class="mt-2 small text-muted" style="margin-left: 1em;"> Tiger version: {{ version }}</div>
          <div class="mt-2 small text-muted" style="margin-left: 1em;"> Build: {{ build }}</div>
        </div>
      </div>

      <div class="col-md-11" id="main-content">

        <!-- execution pane buttons -->
        <nav class="navbar navbar-expand-lg">
          <div class="container-fluid">
            <div class="navbar-nav justify-content-start"></div>
            <div class="navbar-nav execution-pane-nav justify-content-between">
              <a class="btn active execution-pane-buttons" @click="showTab('execution_pane', $event)">Test execution</a>
              <a class="btn execution-pane-buttons" @click="showTab('logs_pane', $event)">Server Logs</a>
            </div>
            <div class="navbar-nav justify-content-end px-5">
              <img alt="gematik logo" class="gematik-logo" src="/img/gematik.svg">
            </div>
          </div>
        </nav>

        <!-- tabs -->
        <div class="tab-content">
          <ExecutionPane :featureUpdateMap="featureUpdateMap" :bannerData="bannerData" :localProxyWebUiUrl="localProxyWebUiUrl" :ui="ui" :started="started"/>
          <ServerLog :serverLogs="serverLogList" :logServers="logServers" :selectedServers="selectedServers" :selectedLoglevel="LogLevel.ALL" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">

/**
 * The communication with the backend is quite complex so here is a basic description of the process:
 *
 * On start up we initiate the channel subscription shortly followed by a fetch to retrieve the current state.
 *
 * Due to racing conditions it could be that before the fetch finishes a message is received via websocket.
 * To not lose this message until the fetch is finished we will collect any incoming message in the preFetchMessageList.
 *
 * When the fetched data is received, the status is set and all received messages are merged
 * (if their index is in correct order and is higher than the fetched index).
 *
 * Finally, the #currentServerStatus is populated indicating the fetch method is done and we can stop collecting preFetch messages.
 *
 * From now on we collect new messages via push.
 *
 * If the index of a new received message is not the next expected index (+1)
 * we do not merge but store this message in the #outOfOrderMessageList and set the timestamp in the field #firstOutOfOrderTimestamp.
 * We do this outermost for 2 seconds hoping to meanwhile receive all missing messages.
 *
 * So on receiving a new message we also check if we can already replay the cached messages
 * (all in order and first cached message is current index +1). If so we empty the cache and reset the timestamp to -1.
 *
 * If 2 seconds have passed we will resort to re fetching the status to avoid having outdated data shown on the page for too long.
 *
 * Sounds complicated and YES it is, but its also safe / defensive and reducing the load on the server
 */

import {onMounted, Ref, ref} from "vue";
import SockJS from "sockjs-client";
import Stomp, {Client, Frame, Message} from "webstomp-client";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";
import TestEnvStatusDto from "@/types/TestEnvStatusDto";
import ServerStatus from "@/components/server/ServerStatus.vue";
import TigerServerStatusDto from "@/types/TigerServerStatusDto";
import BannerMessage from "@/types/BannerMessage";
import ExecutionPane from "@/components/testsuite/ExecutionPane.vue";
import ServerLog from "@/components/serverlog/ServerLog.vue";
import FeatureList from "@/components/testsuite/FeatureList.vue";
import TestStatus from "@/components/testsuite/TestStatus.vue";
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import {currentOverallServerStatus} from "@/types/TigerServerStatus";
import {currentOverallTestRunStatus} from "@/types/testsuite/TestResult";
import Ui from "@/types/ui/Ui";
import BannerType from "@/types/BannerType";
import TigerServerLogDto from "@/types/TigerServerLogDto";
import LogLevel from "@/types/LogLevel";

let baseURL = process.env.BASE_URL;
let socket: WebSocket;
let stompClient: Client;

const started = ref(new Date());

let fetchedInitialStatus = false;

let bannerData: Ref<BannerMessage[]> = ref([]);

/** array to collect any subscription messages coming in while the initial fetch has not completed. */
let preFetchMessageList: Array<TestEnvStatusDto> = new Array<TestEnvStatusDto>();

/** array to cache any incoming messages if we detected an out of order message
 * (so we can not merge as there are some messages missing inbetween).
 */
let outOfOrderMessageList: Array<TestEnvStatusDto> = new Array<TestEnvStatusDto>();

/** timestamp when we detected the first out of order message, after 2 seconds we will do a refetch,
 * but we hope that in the meantime the missing message(s) are coming in, so we can merge and spare the fetch.
 */
let firstOutOfOrderTimestamp: number = -1;

/** index of current state. */
let currentMessageIndex: number = -1;

/** list of status of all servers wehave received any message so far. */
let currentServerStatus: Ref<Map<string, TigerServerStatusDto>> = ref(new Map<string, TigerServerStatusDto>());

/** complex map of features which contain a map of scenarios, which contain a map of steps,
 * representing the current state of the test run.
 */
let featureUpdateMap: Ref<Map<string, FeatureUpdate>> = ref(new Map<string, FeatureUpdate>());

/** list of server logs which contain a log message, a timestamp, a server name and the log level.
 */
let serverLogList: Ref<Array<TigerServerLogDto>> = ref(new Array<TigerServerLogDto>());

let logServers: Ref<Array<string>> = ref (new Array<string>());

let selectedServers: Ref<Array<string>> = ref (new Array<string>("__all__"));

let localProxyWebUiUrl: Ref<string> = ref("");

let version: Ref<string> = ref("");
let build: Ref<string> = ref("");

let ui = ref(new Ui());

onMounted(() => {
  ui = ref(new Ui());
  connectToWebSocket();
  fetchInitialServerStatus();
  fetchTigerVersion();
  fetchTigerBuild();
});

const DEBUG = true;

function debug(message: string) {
  if (DEBUG) {
    console.log(Date.now() + " " + message);
  }
}

function showTab(tabid: string, event: MouseEvent) {
  event.preventDefault();
  const buttons = document.getElementsByClassName('execution-pane-buttons');
  const tabs = document.getElementsByClassName('execution-pane-tabs');
  for (let i = 0; i < buttons.length; i++) {
    buttons[i].classList.toggle("active", false);
    tabs[i].classList.toggle("active", false);
    tabs[i].classList.toggle("show", false);
  }
  document.getElementById(tabid)?.classList.toggle("active", true);
  document.getElementById(tabid)?.classList.toggle("show", true);
  (event.target as HTMLElement)?.classList?.toggle("active", true);
  (event.target as HTMLElement)?.classList.toggle("show", true);
}

/** process any incoming messages. */
function connectToWebSocket() {
  socket = new SockJS(baseURL + "testEnv");
  stompClient = Stomp.over(socket, {debug: false});
  stompClient.connect(
      {},
      () => {
        stompClient.subscribe(baseURL + "topic/envStatus", (tick: Message) => {
          const json = JSON.parse(tick.body);

          debug("RECEIVED " + json.index + "\n" + tick.body);

          if (!json.servers) json.servers = {};
          const pushedMessage: TestEnvStatusDto = new TestEnvStatusDto();
          pushedMessage.index = json.index;
          pushedMessage.bannerMessage = json.bannerMessage;
          pushedMessage.bannerColor = json.bannerColor;
          if (json.bannerType) {
            pushedMessage.bannerType = json.bannerType as BannerType;
          }

          if (json.featureMap) {
            FeatureUpdate.addToMapFromJson(pushedMessage.featureMap, json.featureMap);
          }
          if (json.servers) {
            TigerServerStatusUpdateDto.addToMapFromJson(pushedMessage.servers, json.servers);
          }

          // Deal with initial phase buffering all notifications till fetch returned data
          if (!fetchedInitialStatus) {
            debug("MESSAGE PREFETCH: " + pushedMessage.index);
            preFetchMessageList.push(pushedMessage);
            return;
          }

          replayingCachedMessages();

          debug("Check push message order " + pushedMessage.index + " ?== " + (currentMessageIndex + 1));
          if (pushedMessage.index > currentMessageIndex + 1) {
            // out of order message received
            if (firstOutOfOrderTimestamp === -1) {
              firstOutOfOrderTimestamp = Date.now();
            }
            if (Date.now() - firstOutOfOrderTimestamp > 1000) {
              // resorting to re fetch the status
              firstOutOfOrderTimestamp = -1;
              outOfOrderMessageList = new Array<TestEnvStatusDto>();
              currentServerStatus.value.clear();
              console.warn(Date.now() + ` Missing push messages for more then 1 second in range > ${currentMessageIndex} and < ${pushedMessage.index} ! Triggering refetch`);
              currentMessageIndex = -1;
              preFetchMessageList = new Array<TestEnvStatusDto>();
              preFetchMessageList.push(pushedMessage);
              fetchInitialServerStatus();
            } else {
              // adding message to cache
              outOfOrderMessageList.push(pushedMessage);
              TestEnvStatusDto.sortArray(outOfOrderMessageList);
              console.warn(Date.now() + ` Missing push messages in range > ${currentMessageIndex} and < ${pushedMessage.index} ! Cached message ${pushedMessage.index}`);
            }
          } else {
            // TODO evt. there could be earlier messages coming very late??
            mergeMessage(currentServerStatus.value, pushedMessage);
            replayingCachedMessages();
            debug("MERGE DONE " + currentMessageIndex);
          }

        });
      },
      (error: Frame | CloseEvent) => {
        console.error("Websocket error: " + JSON.stringify(error));
      }
  );



  socketLog = new SockJS(baseURL + "testLog");
  stompClientForLogs = Stomp.over(socketLog, {debug: false});
  stompClientForLogs.connect(
      {},
      () => {
        stompClientForLogs.subscribe(baseURL + "topic/serverLog", (tick: Message) => {
          debug("RECEIVED LOG " + tick.body);
          const receivedLogMessage: TigerServerLogDto = TigerServerLogDto.fromJson(JSON.parse(tick.body));

          if (logServers.value.indexOf(receivedLogMessage.serverName as string) === -1) {
            logServers.value.push(receivedLogMessage.serverName as string);
          }
          const index = serverLogList.value.findIndex((msg) => receivedLogMessage.localDateTime.isAfter(msg.localDateTime))
          serverLogList.value.splice(index, 0, receivedLogMessage);
        });
      },
      (error: Frame | CloseEvent) => {
        console.error("Websocket error: " + JSON.stringify(error));
      }
  );
}

let socketLog: WebSocket;
let stompClientForLogs: Client;


function replayingCachedMessages() {
  debug("Check for replaying cached messages " + outOfOrderMessageList.length);
  if (outOfOrderMessageList.length) {
    debug("First cached index:" + outOfOrderMessageList[0].index + " vs " + currentMessageIndex);
    if (outOfOrderMessageList[0].index === currentMessageIndex + 1
        && TestEnvStatusDto.checkMessagesInArrayAreWellOrdered(outOfOrderMessageList)) {
      debug("REPLAYING cached messages");
      outOfOrderMessageList.forEach(cachedMessage => {
        mergeMessage(currentServerStatus.value, cachedMessage);
      });
      outOfOrderMessageList = new Array<TestEnvStatusDto>();
      firstOutOfOrderTimestamp = -1;
    } else {
      debug("Still missing some messages in cache, so wait");
    }
  }
}

function mergeMessage(map: Map<string, TigerServerStatusDto>, message: TestEnvStatusDto) {
  debug("MESSAGE MERGE: " + message.index);
  updateServerStatus(map, message.servers);
  updateFeatureMap(message.featureMap);
  if (message.bannerMessage) {
    const bm = new BannerMessage();
    bm.text = message.bannerMessage;
    bm.color = message.bannerColor;
    bm.type = message.bannerType;
    bannerData.value.push(bm);
  }
  currentMessageIndex = message.index;
}

function fetchInitialServerStatus() {
  fetch(baseURL + "status")
  .then((response) => response.text())
  .then((data) => {
    debug("FETCH: " + data);
    const json = JSON.parse(data);

    localProxyWebUiUrl.value = json.localProxyWebUiUrl;

    const fetchedServerStatus = new Map<string, TigerServerStatusDto>();
    TigerServerStatusDto.addToMapFromJson(fetchedServerStatus, json.servers);

    if (currentServerStatus.value.size !== 0) {
      console.error("Fetching while currentServerStatus is set is not supported!")
      return;
    }

    // sort prefetched Messages based on index;
    TestEnvStatusDto.sortArray(preFetchMessageList);

    // if notification list is missing a message (index not increased by one) abort and fetch anew assuming we might get a more current state
    let indexConsistent = TestEnvStatusDto.checkMessagesInArrayAreWellOrdered(preFetchMessageList);
    if (!indexConsistent) {
      debug("prefetched message list is not consistent \nwait 500ms and refetch!");
      // TODO add them to the outOfOrderMessage list and return
      window.setTimeout(fetchInitialServerStatus, 500);
      return;
    }

    featureUpdateMap.value.clear();
    FeatureUpdate.addToMapFromJson(featureUpdateMap.value, json.featureMap);
    debug("FETCH FEATURE MERGE DONE");

    if (json.bannerMessage) {
      bannerData.value.splice(0, bannerData.value.length);
      bannerData.value.push(BannerMessage.fromJson(json));
    }

    preFetchMessageList.forEach((testEnvStatusDtoMessage) => {
      if (testEnvStatusDtoMessage.index > json.currentIndex) {
        mergeMessage(fetchedServerStatus, testEnvStatusDtoMessage);
      }
    });
    currentMessageIndex = json.currentIndex;
    fetchedServerStatus.forEach((value, key) => currentServerStatus.value.set(key, value));
    fetchedInitialStatus = true;
    debug("FETCH DONE " + currentMessageIndex);
  });
}

function fetchTigerVersion() {
  fetch(baseURL + "status/version")
  .then((response) => response.text())
  .then((data) => {
    debug("FETCH Version: " + data);
    version.value = data;
  });
}

function fetchTigerBuild() {
  fetch(baseURL + "status/build")
  .then((response) => response.text())
  .then((data) => {
    debug("FETCH Build: " + data);
    build.value = data;
  });
}

function updateServerStatus(serverStatus: Map<string, TigerServerStatusDto>, update: Map<string, TigerServerStatusUpdateDto>) {
  update.forEach((value: TigerServerStatusUpdateDto, key: string) => {
        if (serverStatus.has(key)) {
          const statusUpdate = serverStatus.get(key) as TigerServerStatusDto;
          if (statusUpdate) {
            statusUpdate.mergeFromUpdateDto(value);
          } else {
            console.error(`No or empty status update received for server ${key}`);
          }
        } else {
          serverStatus.set(key, TigerServerStatusDto.fromUpdateDto(key, value));
        }
      }
  );
}

function updateFeatureMap(update: Map<string, FeatureUpdate>) {
  update.forEach((featureUpdate: FeatureUpdate, featureKey: string) => {
    if (featureUpdate.description) {
      debug("FEATURE UPDATE " + featureUpdate.description);
      const featureToBeUpdated: FeatureUpdate | undefined = featureUpdateMap.value.get(featureKey);
      if (!featureToBeUpdated) {
        // add new feature
        debug("add new feature " + featureKey + " => " + JSON.stringify(featureUpdate));
        const feature = new FeatureUpdate().merge(featureUpdate);
        featureUpdateMap.value.set(featureKey, feature);
        debug("added new feature " + featureKey + " => " + feature.toString());
      } else {
        featureToBeUpdated.merge(featureUpdate);
      }
    }
  });
}

</script>
<style>
#logs_pane {
  border-left: 1px solid lightgray;
  padding-left: 1rem;
  min-height: 300px;
}

#sidebar-left {
  border-radius: 0.5rem;
  border: 1px solid var(--gem-primary-100);
  background: var(--gem-primary-100);
  color: var(--gem-primary-400);
  min-height: 100vh;
}

.sidebar-title {
  background: var(--gem-primary-100);
  color: var(--gem-primary-400);
  min-height: 4rem;
  line-height: 4rem;
}

#sidebar-left h4 {
  color: var(--gem-neutral-700);
  padding-left: 0.75rem;
  margin-top: 2rem;
}

.sidebar-collapsed {
  max-width: 60px;
}

.sidebar-collapsed .alert, .sidebar-collapsed h4 > span, .sidebar-collapsed h3 > span,
.sidebar-collapsed i.resizer-left-icon, .sidebar-collapsed .container {
  display: none;
}

i.resizer-left-icon {
  color: var(--gem-primary-400);
  border-radius: 0.5rem;
  padding: 0.5rem;
  background: var(--gem-primary-025);
  text-align: right;
  margin: 1rem 1rem 1rem 0;
  float: right;
  font-size: 60%;
  cursor: pointer;
}


.sidebar-collapsed h4, .sidebar-collapsed h3 {
  text-align: center;
  padding-left: 0 !important;
  cursor: pointer;
}

.sidebar-collapsed h3 img {
  padding: 0;
  margin: 0;
  cursor: pointer;
}

.sidebar-collapsed h4 > i {
  padding-right: 0 !important;
  margin: 1rem 0;
}

.serverstatus-new {
  color: var(--gem-warning-400) !important;
}

.serverstatus-starting {
  color: #0dcaf0 !important;
}

.serverstatus-running {
  color: var(--gem-success-400) !important;
}

.serverstatus-stopped {
  color: var(--gem-error-400) !important;
}

.execution-pane-nav {
  background: var(--gem-primary-100);
  padding: 0.5rem;
  border-radius: 0.5rem;
}

.execution-pane-buttons {
  border: 1px solid var(--gem-primary-100);
  border-radius: 1rem;
  color: var(--gem-primary-400);
}

.execution-pane-buttons.active {
  background: #FCFCFD;
  color: var(--bs-primary);
  cursor: default;
}

.footer-spacing {
  margin-top: 20rem;
}
</style>