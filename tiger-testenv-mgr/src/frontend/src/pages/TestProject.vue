<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div>
    <div class="row">
      <div class="col-md-3" id="sidebar-left">
        <h3 class="sidebar-title">
          <img v-on:click="toggleLeftSideBar(1, $event)" class="navbar-brand right" src="img/tiger-mono-64.png" width="36" alt="Tiger logo"/>
          <span>Workflow UI</span>
          <i v-on:click="toggleLeftSideBar(0, $event)" class="fa-solid fa-circle-chevron-left resizer-left-icon"></i>
        </h3>

        <h4 :class="`${currentOverallTestRunStatus()}`"><i class="fa-solid fa-square-poll-vertical left" v-on:click="toggleLeftSideBar(1, $event)"></i>
          <span>Status</span>
        </h4>
        <TestStatus :featureUpdateMap="featureUpdateMap"/>
        <h4><i class="fa-solid fa-address-card left" v-on:click="toggleLeftSideBar(1, $event)"></i>
          <span>Features</span>
        </h4>
        <FeatureList :featureUpdateMap="featureUpdateMap"/>
        <h4 :class="`serverstatus-${currentOverallServerStatus()}`"><i class="fa-solid fa-server left" v-on:click="toggleLeftSideBar(1, $event)"></i>
          <span>Servers</span>
        </h4>
        <ServerStatus :serverStatusData="currentServerStatus"/>
      </div>
      <div class="col-md-9" id="main-content">
        <nav class="nav nav-tabs nav-fill" role="navigation">
          <!--suppress HtmlUnknownAnchorTarget -->
          <a class="nav-link active" data-bs-toggle="tab" href="#execution_pane" role="tab" data-toggle="tab">Test execution</a>
          <a class="nav-link" data-bs-toggle="tab" href="#logs_pane" role="tab" data-toggle="tab">Server Logs</a>
        </nav>

        <!-- Tab panes -->
        <div class="tab-content">
          <ExecutionPane :featureUpdateMap="featureUpdateMap" :bannerData="bannerData" :localProxyWebUiUrl="localProxyWebUiUrl"/>
          <div class="tab-pane h-100 w-100 text-danger pt-3" id="logs_pane" role="tabpanel">
            <i class="fa-solid fa-circle-exclamation fa-2x left"></i> Not implemented so far
          </div>
        </div>
        <!--BannerMessage :bannerData="bannerData" /-->
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
import BannerMessages from "@/types/BannerMessages";
import ExecutionPane from "@/components/testsuite/ExecutionPane.vue";
import FeatureList from "@/components/testsuite/FeatureList.vue";
import TestStatus from "@/components/testsuite/TestStatus.vue";
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import TigerServerStatus from "@/types/TigerServerStatus";
import TestResult from "@/types/testsuite/TestResult";

let baseURL = process.env.BASE_URL;
let socket: WebSocket;
let stompClient: Client;

let bannerData: Ref<BannerMessages[]> = ref([]);

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

let localProxyWebUiUrl: Ref<string> = ref("");

function mergeStatus(status1: TigerServerStatus, status2: TigerServerStatus) {
  switch (status1) {
    case TigerServerStatus.STARTING:
    case TigerServerStatus.STOPPED:
      return status1;
    case TigerServerStatus.NEW:
    case TigerServerStatus.RUNNING:
      return status2;
  }
}

function currentOverallServerStatus() {
  let status =  TigerServerStatus.NEW;
  currentServerStatus.value.forEach((server, key) => {
    status = mergeStatus(status, server.status);
  });
  return status.toLowerCase();
}

function currentOverallTestRunStatus()   {
  if (!featureUpdateMap.value.size) {
    return "pending";
  }
  let status = "passed";
  featureUpdateMap.value.forEach(feature => {
    if (feature.status === TestResult.FAILED) {
      status = "failed";
    }
    feature.scenarios.forEach(scenario => {
      if (scenario.status === TestResult.FAILED) {
        status = "failed";
      }
    });
  });
  return status;
}

const DEBUG = false;

function debug(message: string) {
  if (DEBUG) {
    console.log(Date.now() + " " + message);
  }
}

function toggleLeftSideBar(open : number, event: MouseEvent) {
  const sidebar: HTMLDivElement = document.getElementById("sidebar-left") as HTMLDivElement;
  const mainContent: HTMLDivElement = document.getElementById("main-content") as HTMLDivElement;
  let classes: string = sidebar.getAttribute("class") as string;
  let mainClasses: string = mainContent.getAttribute("class") as string;

  if (open === 0) {
    sidebar.setAttribute("class", classes.replace("col-md-3", "sidebar-collapsed"))
    mainContent.setAttribute("class", mainClasses.replace("col-md-9", "col-md-11"))
  } else {
    sidebar.setAttribute("class", classes.replace("sidebar-collapsed", "col-md-3"))
    mainContent.setAttribute("class", mainClasses.replace("col-md-11", "col-md-9"))
  }
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
          let pushedMessage: TestEnvStatusDto = {
            featureMap: new Map<string, FeatureUpdate>(),
            index: json.index,
            servers: new Map<string, TigerServerStatusUpdateDto>(Object.entries(json.servers)),
            bannerMessage: json.bannerMessage,
            bannerColor: json.bannerColor
          };
          if (json.featureMap) {
            FeatureUpdate.addToMapFromJson(pushedMessage.featureMap, json.featureMap);
          }

          // Deal with initial phase buffering all notifications till fetch returned data
          if (currentServerStatus.value.size === 0) {
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
}

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
    const bm = new BannerMessages();
    bm.text = message.bannerMessage;
    bm.color = message.bannerColor;
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

    const fetchedServerStatus = new Map<string, TigerServerStatusDto>(Object.entries(json.servers));

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
      bannerData.value.push(BannerMessages.fromJson(json));
    }

    preFetchMessageList.forEach((testEnvStatusDtoMessage) => {
      if (testEnvStatusDtoMessage.index > json.currentIndex) {
        mergeMessage(fetchedServerStatus, testEnvStatusDtoMessage);
      }
    });
    currentMessageIndex = json.currentIndex;
    fetchedServerStatus.forEach((value, key) => currentServerStatus.value.set(key, value));
    debug("FETCH DONE " + currentMessageIndex);
  });
}

function updateServerStatus(serverStatus: Map<string, TigerServerStatusDto>, update: Map<string, TigerServerStatusUpdateDto>) {
  update.forEach((value: TigerServerStatusUpdateDto, key: string) => {
        if (serverStatus.has(key)) {
          const statusUpdate: TigerServerStatusDto | undefined = serverStatus.get(key);
          if (statusUpdate) {
            // update
            if (value.type) {
              statusUpdate.type = value.type;
            }
            if (value.baseUrl) {
              statusUpdate.baseUrl = value.baseUrl;
            }
            if (value.status) {
              statusUpdate.status = value.status;
            }
            if (value.statusMessage) {
              statusUpdate.statusMessage = value.statusMessage;
              statusUpdate.statusUpdates.push(value.statusMessage);
            }
          }
        } else {
          // add
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

onMounted(() => {
  connectToWebSocket();
  fetchInitialServerStatus();
});

</script>
<style>
#logs_pane {
  border-left: 1px solid lightgray;
  padding-left: 1rem;
  min-height: 300px;
}

#sidebar-left {
  color: white;
  background: var(--bs-primary);
  border-radius: 0.5rem;
}

.sidebar-title {
  background: lightslategray;
  min-height:4rem;
  line-height:4rem;
}

#sidebar-left .alert {
  color: var(--bs-primary);
}

#sidebar-left h4 {
  color: white;
  padding-left: 0.75rem;
  margin-top: 2rem;
}

.sidebar-collapsed {
  max-width: 60px;
}

.sidebar-collapsed .alert, .sidebar-collapsed h4 > span, .sidebar-collapsed h3 > span {
  display: none;
}

i.resizer-left-icon {
  color: white;
  text-align: right;
  margin: 1rem 1rem 1rem 0;
  float: right;
}


.sidebar-collapsed i.resizer-left-icon, .sidebar-collapsed .container {
  display: none;
}

.sidebar-collapsed h4, .sidebar-collapsed h3 {
  text-align: center;
  padding-left: 0 !important;
}

.sidebar-collapsed h3 img {
  padding: 0;
  margin: 0;
}

.sidebar-collapsed h4 > i {
  padding-right: 0 !important;
  margin: 1rem 0;
}

.serverstatus-new {
  color: yellow !important;
}
.serverstatus-starting {
  color: #0dcaf0 !important;
}
.serverstatus-running {
  color: forestgreen !important;
}
.serverstatus-stopped {
  color: orangered !important;
}

</style>
