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
  <div>
    <div class="row">
      <!-- side bar -->
      <div
        id="sidebar-left"
        :class="`${sideBarCollapsed ? 'sidebar-collapsed test-sidebar-collapsed' : 'col-md-3 test-sidebar-open'} ${quitTestrunOngoing ? 'sidebar-state-quit test-sidebar-quit' : pauseTestrunOngoing ? 'sidebar-state-paused test-sidebar-paused' : ''}`"
      >
        <!-- side bar title -->
        <h3
          :class="`sidebar-title ${quitTestrunOngoing ? 'sidebar-state-quit' : pauseTestrunOngoing ? 'sidebar-state-paused' : ''}`"
        >
          <img
            id="test-tiger-logo"
            class="navbar-brand"
            src="/img/tiger-mono-64.png"
            width="36"
            alt="Tiger logo"
            @click="toggleLeftSideBar"
          />
          <span id="test-sidebar-title">Workflow UI</span>
          <button
            type="button"
            class="btn btn-sm mt-3 float-end resizer-left-icon"
            @click="toggleLeftSideBar"
          >
            <i
              id="test-sidebar-close-icon"
              class="fa-lg fa-solid fa-angles-left"
            ></i>
          </button>
        </h3>
        <!-- interaction buttons -->
        <div class="toolbar rounded bg-light m-2 p-2 engraved">
          <button
            type="button"
            title="Quit test run"
            :class="`btn btn-sm btn-danger m-1 ${quitTestrunOngoing ? 'disabled active' : 'enabled'}`"
            @click="quitTestrun"
          >
            <i
              id="test-sidebar-quit-icon"
              class="fa-lg fa-solid fa-power-off fa-fw"
            ></i>
          </button>
          <button
            type="button"
            title="Pause test run"
            :class="`btn btn-sm m-1 ${pauseTestrunOngoing && !quitTestrunOngoing ? 'btn-success' : 'btn-warning'} ${quitTestrunOngoing ? 'disabled' : ''}`"
            @click="pauseTestrun"
          >
            <i
              id="test-sidebar-pause-icon"
              :class="`fa-lg fa-solid ${pauseTestrunOngoing && !quitTestrunOngoing ? 'fa-play' : 'fa-pause'} fa-fw`"
            ></i>
          </button>
          <button
            id="test-sidebar-tg-config-editor-icon"
            type="button"
            title="Configuration Editor"
            :class="`btn btn-sm m-1 btn-secondary ${quitTestrunOngoing ? 'disabled active' : 'enabled'}`"
            @click="() => (configEditorSidePanelIsOpened = true)"
          >
            <i class="fa-lg fa-solid fa-gears fa-fw"></i>
          </button>
          <div v-if="hasTestRunFinished">
            <div
              id="test-sidebar-stop-message"
              style="color: red"
              class="finishedMessage"
            >
              Test run finished, press QUIT button
            </div>
          </div>

          <VueSidePanel
            v-model="configEditorSidePanelIsOpened"
            side="left"
            width="85%"
            lock-scroll
            hide-close-btn
          >
            <template #header>
              <div class="container">
                <h1 style="color: var(--gem-primary-400)">
                  Tiger Global Configuration Editor
                  <span
                    id="test-tg-config-editor-btn-close"
                    class="float-end"
                    role="button"
                    @click="configEditorSidePanelIsOpened = false"
                    ><i class="fa fa-window-close" />
                  </span>
                </h1>
              </div>
            </template>
            <template #default>
              <div class="container-fluid">
                <div class="row">
                  <TigerConfigurationEditor ref="tigerConfigEditor" />
                </div>
              </div>
            </template>
          </VueSidePanel>
        </div>
        <!-- test run status -->
        <h4>
          <i
            id="test-sidebar-status-icon"
            :class="`${currentOverallTestRunStatus(featuresStore.featureUpdateMap)} fa-xl fa-solid fa-square-poll-vertical left`"
          >
          </i>
          <span id="test-sidebar-status">Status</span>
        </h4>
        <TestStatus
          :feature-update-map="featuresStore.featureUpdateMap"
          :started="started"
        />
        <!-- feature list -->
        <h4>
          <i
            id="test-sidebar-feature-icon"
            class="fa-lg fa-solid fa-address-card left"
          ></i>
          <span id="test-sidebar-feature">Features</span>
        </h4>

        <div class="container">
          <div class="row mb-1">
            <button
              v-if="featureFlags.testSelection"
              class="btn btn-primary"
              type="button"
              @click="openTestSelectorDialog"
              id="open-test-selector-button"
            >
              Select tests to execute...
            </button>
          </div>
        </div>
        <FeatureList :feature-update-map="featuresStore.featureUpdateMap" />
        <!-- server status -->
        <h4>
          <i
            id="test-sidebar-server-icon"
            :class="`serverstatus-${currentOverallServerStatus(currentServerStatus)} fa-xl fa-solid fa-server left`"
          >
          </i>
          <span id="test-sidebar-server">Servers</span>
        </h4>
        <ServerStatus :server-status-data="currentServerStatus" />
        <div class="container">
          <div id="test-sidebar-version" class="mt-2 small text-muted ms-2">
            Tiger version: {{ version }}
          </div>
          <div id="test-sidebar-build" class="mt-2 small text-muted ms-2">
            Build: {{ build }}
          </div>
        </div>
      </div>

      <div
        id="main-content"
        :class="`${sideBarCollapsed ? 'col-md-11' : 'col-md-9'}`"
      >
        <!-- execution pane buttons -->
        <nav class="navbar navbar-expand-lg">
          <div class="container-fluid">
            <div class="navbar-nav justify-content-start"></div>
            <div class="navbar-nav execution-pane-nav justify-content-between">
              <a
                id="test-execution-pane-tab"
                class="btn active execution-pane-buttons"
                @click="showTab('execution_pane', $event)"
                >Test execution</a
              >
              <a
                id="test-server-log-tab"
                class="btn execution-pane-buttons"
                @click="showTab('logs_pane', $event)"
                >Server Logs</a
              >
              <a
                v-if="featureFlags.trafficVisualization"
                id="test-traffic-visualization-tab"
                class="btn execution-pane-buttons"
                @click="showTab('visualization_pane', $event)"
                >Traffic Visualization</a
              >
            </div>
            <div class="navbar-nav justify-content-end px-5">
              <img
                id="test-gematik-logo"
                alt="gematik logo"
                class="gematik-logo"
                src="/img/gematik.svg"
              />
            </div>
          </div>
        </nav>

        <!-- tabs -->
        <div class="tab-content">
          <execution-pane
            :feature-update-map="featuresStore.featureUpdateMap"
            :banner-message="
              bannerData.length ? bannerData[bannerData.length - 1] : false
            "
            :local-proxy-web-ui-url="localProxyWebUiUrl"
            :ui="ui"
            :started="started"
            :quit-testrun-ongoing="quitTestrunOngoing"
            :quit-reason="quitReason"
          />
          <ServerLog
            :server-logs="serverLogList"
            :log-servers="logServers"
            :selected-servers="selectedServers"
            :selected-loglevel="LogLevel.ALL.toString()"
            :selected-text="''"
          />
          <traffic-visualization
            v-if="featureFlags.trafficVisualization"
            :feature-update-map="featuresStore.featureUpdateMap"
            :ui="ui"
          />
        </div>
        <rbel-log-details-pane
          :ui="ui"
          :local-proxy-web-ui-url="localProxyWebUiUrl"
        ></rbel-log-details-pane>
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
 * We do this outermost for 1 second hoping to meanwhile receive all missing messages.
 *
 * So on receiving a new message we also check if we can already replay the cached messages
 * (all in order and first cached message is current index +1). If so we empty the cache and reset the timestamp to -1.
 *
 * If 2 seconds have passed we will resort to re fetching the status to avoid having outdated data shown on the page for too long.
 *
 * Sounds complicated and YES it is, but its also safe / defensive and reducing the load on the server
 */
import { onMounted, provide, ref, type Ref } from "vue";
import SockJS from "sockjs-client";
import Stomp, { Client, Frame, type Message } from "webstomp-client";
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
import { currentOverallServerStatus } from "@/types/TigerServerStatus";
import { currentOverallTestRunStatus } from "@/types/testsuite/TestResult";
import Ui from "@/types/ui/Ui";
import BannerType from "@/types/BannerType";
import TigerServerLogDto from "@/types/TigerServerLogDto";
import LogLevel from "@/types/LogLevel";
import mitt, { type Emitter } from "mitt";
import TigerConfigurationEditor from "@/components/global_configuration/TigerConfigurationEditor.vue";
import "vue3-side-panel/dist/vue3-side-panel.css";
import { VueSidePanel } from "vue3-side-panel";
import TrafficVisualization from "@/components/sequence_diagram/TrafficVisualization.vue";
import { useConfigurationLoader } from "@/components/global_configuration/ConfigurationLoader";
import { FeatureFlags } from "@/types/FeatureFlags.ts";
import RbelLogDetailsPane from "@/components/testsuite/RbelLogDetailsPane.vue";
import type QuitReason from "@/types/QuitReason";
import TestSelector from "@/components/testselector/TestSelector.vue";
import { useDialog } from "primevue";
import { useFeaturesStore } from "@/stores/features.ts";
import debug from "@/logging/log.ts";
import { useTestSuiteLifecycleStore } from "@/stores/testSuiteLifecycle.ts";

const { loadSubsetOfProperties } = useConfigurationLoader();
const featureFlags = ref(new FeatureFlags());

const baseURL = import.meta.env.BASE_URL;
let socket: WebSocket;
let stompClient: Client;

const started = ref(new Date());

let fetchedInitialStatus = false;

const bannerData: Ref<BannerMessage[]> = ref([]);

/** array to collect any subscription messages coming in while the initial fetch has not completed. */
let preFetchMessageList: Array<TestEnvStatusDto> =
  new Array<TestEnvStatusDto>();

/** array to cache any incoming messages if we detected an out of order message
 * (so we can not merge as there are some messages missing inbetween).
 */
let outOfOrderMessageList: Array<TestEnvStatusDto> =
  new Array<TestEnvStatusDto>();

/** timestamp when we detected the first out of order message, after 2 seconds we will do a refetch,
 * but we hope that in the meantime the missing message(s) are coming in, so we can merge and spare the fetch.
 */
let firstOutOfOrderTimestamp: number = -1;

/** index of current state. */
let currentMessageIndex: number = -1;

/** list of status of all servers wehave received any message so far. */
const currentServerStatus: Ref<Map<string, TigerServerStatusDto>> = ref(
  new Map<string, TigerServerStatusDto>(),
);

/** complex map of features which contain a map of scenarios, which contain a map of steps,
 * representing the current state of the test run.
 */
const featuresStore = useFeaturesStore();

/** list of server logs which contain a log message, a timestamp, a server name and the log level.
 */

const serverLogList: Ref<Array<TigerServerLogDto>> = ref(
  new Array<TigerServerLogDto>(),
);

const logServers: Ref<Array<string>> = ref(new Array<string>());

const selectedServers: Ref<Array<string>> = ref(new Array<string>("__all__"));

const localProxyWebUiUrl: Ref<string> = ref("");

const version: Ref<string> = ref("");
const build: Ref<string> = ref("");

let ui = ref();

const quitTestrunOngoing: Ref<boolean> = ref(false);
const pauseTestrunOngoing: Ref<boolean> = ref(false);
const quitReason: Ref<QuitReason> = ref({ message: "", details: "" });

const sideBarCollapsed: Ref<boolean> = ref(true);

const emitter: Emitter<any> = mitt();
provide("emitter", emitter);

const configEditorSidePanelIsOpened: Ref<boolean> = ref(false);

let hasTestRunFinished = false;

async function loadFeaturesFlags() {
  featureFlags.value = FeatureFlags.fromMap(
    await loadSubsetOfProperties("tiger.lib"),
  );
}

onMounted(() => {
  ui = ref(new Ui());
  connectToWebSocket();
  fetchInitialServerStatus();
  fetchTigerVersion();
  fetchTigerBuild();
  loadFeaturesFlags().then(() => checkIfSelectorShouldOpen());
});

const dialog = useDialog();
const openTestSelectorDialog = () => {
  dialog.open(TestSelector, {
    props: {
      modal: true,
      position: "top",
    },
  });
};

function checkIfSelectorShouldOpen() {
  if (featureFlags.value.shouldOpenDialog()) {
    openTestSelectorDialog();
  }
}

function setTestRunFinished() {
  hasTestRunFinished = true;
}

function showTab(tabid: string, event: MouseEvent) {
  event.preventDefault();
  const buttons = document.getElementsByClassName("execution-pane-buttons");
  const tabs = document.getElementsByClassName("execution-pane-tabs");
  for (let i = 0; i < buttons.length; i++) {
    buttons[i].classList.toggle("active", false);
    tabs[i].classList.toggle("active", false);
  }
  document.getElementById(tabid)?.classList.toggle("active", true);
  (event.target as HTMLElement)?.classList?.toggle("active", true);
}

let reloadTimeoutHandle: number;

function checkMessageOrderAndProcessAccordingly(
  pushedMessage: TestEnvStatusDto,
) {
  if (pushedMessage.index > currentMessageIndex + 1) {
    // out of order message received
    if (firstOutOfOrderTimestamp === -1) {
      firstOutOfOrderTimestamp = Date.now();
    }
    if (Date.now() - firstOutOfOrderTimestamp > 200) {
      // resorting to re fetch the status
      firstOutOfOrderTimestamp = -1;
      currentServerStatus.value.clear();
      console.warn(
        Date.now() +
          ` Missing push messages for more then 1 second in range > ${currentMessageIndex} and < ${pushedMessage.index} ! Triggering refetch`,
      );
      currentMessageIndex = -1;
      preFetchMessageList = new Array<TestEnvStatusDto>();
      preFetchMessageList.push(pushedMessage);
      fetchInitialServerStatus();
    } else {
      // adding message to cache
      outOfOrderMessageList.push(pushedMessage);
      TestEnvStatusDto.sortArray(outOfOrderMessageList);
      console.warn(
        Date.now() +
          ` Missing push messages in range > ${currentMessageIndex} and < ${pushedMessage.index} ! Cached message ${pushedMessage.index} firstOutOfOrderMsgTimestamp ` +
          firstOutOfOrderTimestamp,
      );
      reloadTimeoutHandle = setTimeout(() => {
        firstOutOfOrderTimestamp = -1;
        currentServerStatus.value.clear();
        console.warn(
          Date.now() +
            ` TO handler - Missing push messages for more then 1 second in range > ${currentMessageIndex} and < ${pushedMessage.index} ! Triggering refetch`,
        );
        currentMessageIndex = -1;
        preFetchMessageList = new Array<TestEnvStatusDto>();
        preFetchMessageList.push(pushedMessage);
        fetchInitialServerStatus();
      }, 1000);
    }
  } else {
    // TODO evt. there could be earlier messages coming very late??
    mergeMessage(currentServerStatus.value, pushedMessage);
    replayingCachedMessages();
    debug("MERGE DONE " + currentMessageIndex);
  }
}

/** process any incoming messages. */
function connectToWebSocket() {
  socket = new SockJS(baseURL + "testEnv");

  stompClient = Stomp.over(socket, { debug: false });
  stompClient.connect(
    {},
    () => {
      stompClient.subscribe(baseURL + "topic/envStatus", (tick: Message) => {
        const json = JSON.parse(tick.body);

        debug("RECEIVED " + json.index + "\n" + tick.body);

        if (!json.servers) json.servers = {};

        if (json.removedMessageUuids) {
          featuresStore.updateRemovedMessageUuids(json.removedMessageUuids);
        }

        if (json.bannerType) {
          if (json.bannerType === BannerType.TESTRUN_ENDED) {
            setTestRunFinished();
            if (sideBarCollapsed.value) {
              toggleLeftSideBar();
            }
          }
          const pushedMessage: TestEnvStatusDto = new TestEnvStatusDto();
          pushedMessage.index = json.index;
          pushedMessage.bannerMessage = json.bannerMessage;
          pushedMessage.bannerColor = json.bannerColor;
          pushedMessage.bannerIsHtml = json.bannerIsHtml;
          pushedMessage.bannerDetails = json.bannerDetails;
          pushedMessage.testSuiteLifecycle = json.testSuiteLifecycle;
          if (json.bannerType) {
            pushedMessage.bannerType = json.bannerType as BannerType;
          }
          if (json.featureMap) {
            FeatureUpdate.addToMapFromJson(
              pushedMessage.featureMap,
              json.featureMap,
            );
          }
          if (json.servers) {
            TigerServerStatusUpdateDto.addToMapFromJson(
              pushedMessage.servers,
              json.servers,
            );
          }

          // Deal with initial phase buffering all notifications till fetch returned data
          if (!fetchedInitialStatus) {
            debug("MESSAGE PREFETCH: " + pushedMessage.index);
            preFetchMessageList.push(pushedMessage);
            return;
          }

          if (reloadTimeoutHandle) {
            clearTimeout(reloadTimeoutHandle);
          }
          replayingCachedMessages();

          debug(
            "Check push message order " +
              pushedMessage.index +
              " ?== " +
              (currentMessageIndex + 1),
          );
          checkMessageOrderAndProcessAccordingly(pushedMessage);
        }
      });
    },
    (error: Frame | CloseEvent) => {
      if (error?.type === "close") {
        shutdownWorkflowUi({
          message: "Backend of Workflow UI no longer active",
          details:
            "Connection to backend closed. \nRbelLog details pane has no more filtering / search support!",
        });
      }
      console.error("Websocket error: " + JSON.stringify(error));
    },
  );

  socketLog = new SockJS(baseURL + "testLog");
  stompClientForLogs = Stomp.over(socketLog, { debug: false });
  stompClientForLogs.connect(
    {},
    () => {
      stompClientForLogs.subscribe(
        baseURL + "topic/serverLog",
        (tick: Message) => {
          debug("RECEIVED LOG " + tick.body);
          const receivedLogMessage: TigerServerLogDto =
            TigerServerLogDto.fromJson(JSON.parse(tick.body));

          if (
            logServers.value.indexOf(
              receivedLogMessage.serverName as string,
            ) === -1
          ) {
            logServers.value.push(receivedLogMessage.serverName as string);
          }
          const index = serverLogList.value.findIndex((msg) =>
            receivedLogMessage.localDateTime.isAfter(msg.localDateTime),
          );
          serverLogList.value.splice(index, 0, receivedLogMessage);
        },
      );
    },
    (error: Frame | CloseEvent) => {
      console.error("Websocket error: " + JSON.stringify(error));
    },
  );
}

let socketLog: WebSocket;
let stompClientForLogs: Client;

function replayingCachedMessages() {
  debug("Check for replaying cached messages " + outOfOrderMessageList.length);
  if (outOfOrderMessageList.length) {
    debug(
      "First cached index:" +
        outOfOrderMessageList[0].index +
        " vs " +
        currentMessageIndex,
    );
    if (
      outOfOrderMessageList[0].index === currentMessageIndex + 1 &&
      TestEnvStatusDto.checkMessagesInArrayAreWellOrdered(outOfOrderMessageList)
    ) {
      debug("REPLAYING cached messages");
      outOfOrderMessageList.forEach((cachedMessage) => {
        mergeMessage(currentServerStatus.value, cachedMessage);
      });
      outOfOrderMessageList = new Array<TestEnvStatusDto>();
      firstOutOfOrderTimestamp = -1;
    } else {
      debug(
        "Still missing some messages in cache, so wait " +
          (Date.now() - firstOutOfOrderTimestamp),
      );
      debug("oooml: " + JSON.stringify(outOfOrderMessageList));
    }
  }
}

function mergeMessage(
  map: Map<string, TigerServerStatusDto>,
  message: TestEnvStatusDto,
) {
  debug("MESSAGE MERGE: " + message.index);
  updateServerStatus(map, message.servers);
  if (message.testSuiteLifecycle) {
    useTestSuiteLifecycleStore().updateLifecycle(message.testSuiteLifecycle);
  }
  featuresStore.updateFeatureMap(message.featureMap);
  if (message.bannerMessage) {
    const bm = new BannerMessage();
    bm.text = message.bannerMessage;
    bm.color = message.bannerColor;
    bm.type = message.bannerType;
    bm.isHtml = message.bannerIsHtml;
    bannerData.value.push(bm);
  }
  currentMessageIndex = message.index;
}

function fetchInitialServerStatus() {
  fetch(baseURL + "status")
    .then((response) => response.text())
    .then((data) => {
      debug("FETCH initial server status: " + data);
      const json = JSON.parse(data);

      const fetchedServerStatus = new Map<string, TigerServerStatusDto>();
      TigerServerStatusDto.addToMapFromJson(fetchedServerStatus, json.servers);

      if (fetchedServerStatus.has("local_tiger_proxy")) {
        const url = fetchedServerStatus.get("local_tiger_proxy")?.baseUrl;
        if (url) {
          const originalUrl = new URL(url);
          const originalPort = originalUrl.port;
          const winUrl = window.location.host;
          const myArray = winUrl.split(":");
          const newHostname = myArray[0];
          localProxyWebUiUrl.value = `http://${newHostname}:${originalPort}`;
        }
      }

      if (currentServerStatus.value.size !== 0) {
        console.error(
          "Fetching while currentServerStatus is set is not supported!",
        );
        return;
      }

      // sort prefetched Messages based on index;
      TestEnvStatusDto.sortArray(preFetchMessageList);

      // if notification list is missing a message (index not increased by one) abort and fetch anew assuming we might get a more current state
      const indexConsistent =
        TestEnvStatusDto.checkMessagesInArrayAreWellOrdered(
          preFetchMessageList,
        );
      if (!indexConsistent) {
        debug(
          "prefetched message list is not consistent \nwait 500ms and refetch!",
        );
        // TODO add them to the outOfOrderMessage list and return
        window.setTimeout(fetchInitialServerStatus, 500);
        return;
      }

      featuresStore.replaceFeatureMap(json.featureMap);
      debug("FETCH FEATURE MERGE DONE");
      if (json.testSuiteLifecycle) {
        useTestSuiteLifecycleStore().updateLifecycle(json.testSuiteLifecycle);
      }

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
      fetchedServerStatus.forEach((value, key) =>
        currentServerStatus.value.set(key, value),
      );
      fetchedInitialStatus = true;
      debug("FETCH DONE " + currentMessageIndex);
      //TODO now check outOfOrder list if there is any new messages with higher index in list
      debug("OOFList: " + JSON.stringify(outOfOrderMessageList));
      outOfOrderMessageList = new Array<TestEnvStatusDto>();
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

function updateServerStatus(
  serverStatus: Map<string, TigerServerStatusDto>,
  update: Map<string, TigerServerStatusUpdateDto>,
) {
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
    if (key == "local_tiger_proxy") {
      if (value.baseUrl) {
        localProxyWebUiUrl.value = value.baseUrl;
      }
    }
  });
}

function toggleLeftSideBar() {
  document.getElementById("execution_table")?.style.removeProperty("width");
  document.getElementById("workflow-messages")?.style.removeProperty("width");
  sideBarCollapsed.value = !sideBarCollapsed.value;
}

function quitTestrun(ev: MouseEvent) {
  ev.preventDefault();
  fetch(baseURL + "testExecution/quit", { method: "PUT" })
    .then(
      (response) => {
        console.log("RES: " + JSON.stringify(response));
        if (!response.ok) {
          alert("Failed to abort test execution! " + response.statusText);
          return false;
        }
        shutdownWorkflowUi({ message: "Quit on user request!", details: "" });
      },
      (error) => {
        console.log("ERR: " + JSON.stringify(error));
      },
    )
    .catch((error) => {
      console.log("CATCH: " + JSON.stringify(error));
    });
  return false;
}

function pauseTestrun(ev: MouseEvent) {
  pauseTestrunOngoing.value = !pauseTestrunOngoing.value;
  ev.preventDefault();
  fetch(baseURL + "testExecution/pause", { method: "PUT" })
    .then(
      (response) => {
        console.log("RES: " + JSON.stringify(response));
        if (!response.ok) {
          alert("Failed to pause test execution! " + response.statusText);
          return false;
        }
      },
      (error) => {
        console.log("ERR: " + JSON.stringify(error));
      },
    )
    .catch((error) => {
      console.log("CATCH: " + JSON.stringify(error));
    });
  return false;
}

function shutdownWorkflowUi(reason: QuitReason) {
  quitTestrunOngoing.value = true;
  setReasonWithoutReplacing(reason);
}

function setReasonWithoutReplacing(reason: QuitReason) {
  //if the quitReason has already something set, we do not overwrite it. So that if the user explicitly quit,
  //this reason is not overwritten by the subsequent loss of websocket connection.
  quitReason.value = {
    message: quitReason.value.message
      ? quitReason.value.message
      : reason.message,
    details: quitReason.value.details
      ? quitReason.value.details
      : reason.details,
  };
}
</script>
<style>
#logs_pane {
  border-left: 1px solid lightgray;
  padding-left: 1rem;
  min-height: 300px;
}

.navbar-brand {
  margin-left: 0.7rem;
}

#sidebar-left {
  background: var(--gem-primary-100);
  color: var(--gem-primary-400);
  min-height: 100vh;
  box-shadow: 7px 0 3px -4px #888;
  position: sticky;
  overflow-y: auto;
  top: 0;
  max-height: 100vh;
}

#sidebar-left.sidebar-state-paused,
.sidebar-title.sidebar-state-paused {
  background-color: #fdd288;
}

#sidebar-left.sidebar-state-quit,
.sidebar-title.sidebar-state-quit {
  color: red;
  background-color: lightcoral;
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

.sidebar-collapsed .alert,
.sidebar-collapsed h4 > span,
.sidebar-collapsed h3 > span,
.sidebar-collapsed button.resizer-left-icon,
.sidebar-collapsed .container,
.sidebar-collapsed .finishedMessage {
  display: none;
}

.sidebar-collapsed h4,
.sidebar-collapsed h3 {
  text-align: center;
  padding-left: 0 !important;
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

.sidebar-collapsed .toolbar {
  background: none !important;
  padding: 0 !important;
  box-shadow: none;
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
  background: #fcfcfd;
  color: var(--bs-primary);
  cursor: default;
}

.footer-spacing {
  margin-top: 20rem;
}

.steps-docstring {
  color: #0a8694;
  font-family: Courier, monospace;
  padding-left: 1rem;
  white-space: pre;
}

.step_text .table-data-table td {
  border: 1px solid #aaa;
  padding: 0.25rem;
  color: #0a8694;
}

.step_text .table-data-table {
  margin-top: 0.25rem;
  margin-left: 2rem;
  margin-bottom: 0.5rem;
}

.w-18px {
  width: 18px;
}

.engraved {
  box-shadow: inset 0 5px 10px 0 rgba(0, 0, 0, 0.25);
  border: none;
}

.ag-cell-focus,
.ag-cell-no-focus {
  border: none !important;
}

/* This CSS is to not apply the border for the column having 'no-border' class */
.no-border.ag-cell:focus {
  border: none !important;
  outline: none;
}
</style>
