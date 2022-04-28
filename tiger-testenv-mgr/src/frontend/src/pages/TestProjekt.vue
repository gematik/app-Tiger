<template>
  <div>
    <div class="row" style="margin-top: 30px">
      <div class="col-md-3">
        <ServerStatus :serverStatusData="currentServerStatus"/>
        <FeatureList :featureUpdateMap="featureUpdateMap"/>
      </div>
      <div class="col-md-9">
        <nav class="nav nav-tabs nav-fill" role="tablist">
          <a class="nav-link active" data-bs-toggle="tab" href="#execution_pane" role="tab" data-toggle="tab">Test execution</a>
          <a class="nav-link" data-bs-toggle="tab" href="#logs_pane" role="tab" data-toggle="tab">Server Logs</a>
        </nav>

        <!-- Tab panes -->
        <div class="tab-content">
          <ExecutionPane :featureUpdateMap="featureUpdateMap" :bannerData="bannerData"/>
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
import {onMounted, Ref, ref} from "vue";
import SockJS from "sockjs-client";
import Stomp, {Client, Frame, Message} from "webstomp-client";
import TigerEnvStatusDto from "@/types/TigerEnvStatusDto";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";
import TestEnvStatusDto from "@/types/TestEnvStatusDto";
import ServerStatus from "@/components/server/ServerStatus.vue";
import TigerServerStatusDto from "@/types/TigerServerStatusDto";
import BannerMessages from "@/types/BannerMessages";
import ExecutionPane from "@/components/testsuite/ExecutionPane.vue";
import FeatureList from "@/components/testsuite/FeatureList.vue";
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import ScenarioUpdate from "@/types/testsuite/ScenarioUpdate";
import StepUpdate from "@/types/testsuite/StepUpdate";
import TestResult from "@/types/testsuite/TestResult";

let baseURL = process.env.BASE_URL;
let socket: any;
let stompClient: Client;

let bannerData: Ref<BannerMessages[]> = ref([]);

let preFetchMessageList: Array<TestEnvStatusDto> = new Array<TestEnvStatusDto>();
let currentServerStatus: Ref<Map<string, TigerServerStatusDto>> = ref(new Map<string, TigerServerStatusDto>());
let currentMessageIndex: number = -1;

let featureUpdateMap: Ref<Map<string, FeatureUpdate>> = ref(new Map<string, FeatureUpdate>());

const DEBUG = false;

// TODO initialization process:
// DONE collect notifications via Push in sorted list (based on index)
// DONE fetch
// sort prefetched Messages based on index;
// when fetched check currentIndex in fetch and apply only those notifications that have higher index
// if notification list is missing a message (index not increased by one) abort and fetch anew assuming we might get a more current state
// DONE after fetch is finished, dont collect notifications anymore but apply them directly

function connectToWebSocket() {
  socket = new SockJS(baseURL + "testEnv");
  stompClient = Stomp.over(socket, {debug: false});
  stompClient.connect(
      {},
      () => {
        stompClient.subscribe(baseURL + "topic/envStatus", (tick: Message) => {

          const json = JSON.parse(tick.body);
          if (!json.featureMap) json.featureMap = {};
          if (!json.servers) json.servers = {};
          let pushedMessage: TestEnvStatusDto = {
            featureMap: new Map<string, FeatureUpdate>(Object.entries(json.featureMap)),
            index: json.index,
            servers: new Map<string, TigerServerStatusUpdateDto>(Object.entries(json.servers))
          };

          // Deal with initial phase buffering all notifications till fetch returned data
          if (currentServerStatus.value.size === 0) {
            if (DEBUG) console.log("MESSAGE PREFETCH: " + tick.body);
            preFetchMessageList.push(pushedMessage);
            return;
          }

          if (DEBUG) console.log("Check push message order");
          if (pushedMessage.index > currentMessageIndex + 1) {
            currentServerStatus.value.clear();
            console.warn(`Missing push messages in range > ${currentMessageIndex} and < ${pushedMessage.index} ! Triggering refetch`);
            currentMessageIndex = -1;
            preFetchMessageList = new Array<TestEnvStatusDto>();
            preFetchMessageList.push(pushedMessage);
            fetchInitialServerStatus();
            return;
          }

          if (DEBUG) console.log("MESSAGE MERGE: " + tick.body);
          // from now on merge notifications directly into serverStatus
          if (pushedMessage.servers !== null) {
            // server update
            updateServerStatus(currentServerStatus.value, pushedMessage.servers);
          }
          if (pushedMessage.featureMap !== null) {
            updateFeatureMap(pushedMessage.featureMap);
          }

          if (json.bannerMessage) {
            const bm = new BannerMessages();
            bm.text = json.bannerMessage;
            bm.color = json.bannerColor;
            bannerData.value.push(bm);
          }
          currentMessageIndex = pushedMessage.index;
          if (DEBUG) console.log("MERGE DONE " + currentMessageIndex);
        });
      },
      (error: Frame | CloseEvent) => {
        console.log("Websocket error: " + error);
      }
  );
}

function fetchInitialServerStatus() {
  fetch(baseURL + "status")
  .then((response) => response.text())
  .then((data) => {

    if (DEBUG) console.log("FETCH: " + data);

    const json = JSON.parse(data);
    const fetchedServerStatus = new Map<string, TigerServerStatusDto>(Object.entries(json.servers));

    let response: TestEnvStatusDto = {
      featureMap: new Map<string, FeatureUpdate>(Object.entries(json.featureMap)),
      index: json.index,
      servers: new Map<string, TigerServerStatusDto>(Object.entries(json.servers))
    };

    if (currentServerStatus.value.size === 0) {
      // sort prefetched Messages based on index;
      preFetchMessageList.sort((a, b) => {
        return Number(a.index - b.index)
      });

      // if notification list is missing a message (index not increased by one) abort and fetch anew assuming we might get a more current state
      let ctr: number = -1;
      let indexConsistent = true;
      preFetchMessageList.every((testEnvStatusDtoMessage) => {
        const index: number = testEnvStatusDtoMessage.index;
        if (ctr === -1) {
          ctr = index;
          return true;
        } else {
          if (ctr + 1 !== index) {
            indexConsistent = false;
            return false;
          }
        }
      });
      if (!indexConsistent) {
        console.log("prefetched message list is not consistent \nwait 500ms and refetch!");
        // TODO actually wait 500ms and recheck as messages might have been pushed
        window.setTimeout(fetchInitialServerStatus, 500);
        return;
      }
    } else {
      console.error("Fetching while currentServerStatus is set is not supported!")
      return;
    }

    FeatureUpdate.addToMapFromJson(featureUpdateMap.value, json.featureMap);
    if (DEBUG) console.log("FETCH FEATURE MERGE DONE");
    // DEBUG console.log("Features: " + FeatureUpdate.mapToString(featureUpdateMap.value));

    preFetchMessageList.forEach((testEnvStatusDtoMessage) => {
      if (testEnvStatusDtoMessage.index > json.currentIndex && testEnvStatusDtoMessage.servers !== null) {
        testEnvStatusDtoMessage.servers.forEach((value: TigerServerStatusUpdateDto, key: string) => {
          if (fetchedServerStatus.get(key) !== null) {
            if (value.type !== TestResult.UNUSED) {
              fetchedServerStatus.get(key).type = value.type;
            }
            if (value.baseUrl) {
              fetchedServerStatus.get(key).baseUrl = value.baseUrl;
            }
            if (value.statusMessage) {
              fetchedServerStatus.get(key).statusMessage = value.statusMessage;
            }
          } else {
            fetchedServerStatus.set(key, value);
          }
        });
      }
      // TODO replace the subseuent code with add/merge from the Feature/Scenario/StepUpdate classes
      if (testEnvStatusDtoMessage.featureMap !== null) {
        updateFeatureMap(new Map<string, FeatureUpdate>(Object.entries(testEnvStatusDtoMessage.featureMap)));
      }
      // TODO bannermessage aus updates ziehen
    });

    if (json.bannerMessage) {
      const bm = new BannerMessages();
      bm.text = json.bannerMessage;
      bm.color = json.bannerColor;
      bannerData.value.splice(0, bannerData.value.length);
      bannerData.value.push(bm);
    }
    currentMessageIndex = json.currentIndex;
    fetchedServerStatus.forEach((value, key) => currentServerStatus.value.set(key, value));
    if (DEBUG) console.log("FETCH DONE " + currentMessageIndex);
  });
}

function updateServerStatus(serverStatus: Map<string, TigerServerStatusDto>, update: Map<string, TigerServerStatusUpdateDto>) {
  update.forEach((value: TigerServerStatusUpdateDto, key: string) => {
        if (serverStatus.has(key)) {
          // update
          if (value.type !== null) {
            serverStatus.get(key).type = value.type;
          }
          if (value.baseUrl) {
            serverStatus.get(key).baseUrl = value.baseUrl;
          }
          if (value.statusMessage !== null) {
            serverStatus.get(key).statusMessage = value.statusMessage;
            serverStatus.get(key).statusUpdates.push(value.statusMessage);
          }
        } else {
          // add
          serverStatus.set(key, value);
        }
      }
  );
}

function updateFeatureMap(update: Map<string, FeatureUpdate>) {
  if (update !== null) {
    update.forEach((featureUpdate: FeatureUpdate, featureKey: string) => {
      let featureToBeUpdated: FeatureUpdate | undefined = featureUpdateMap.value.get(featureKey);
      if (featureToBeUpdated === undefined) {
        // add new feature
        addNewFeatureToMap(featureUpdate, featureKey);
      } else {
        // feature in map -> check scenarios and steps
        checkFeatureMapForUpdates(featureUpdate, featureToBeUpdated);
      }
    });
  }
}

function checkFeatureMapForUpdates(
    featureUpdate: FeatureUpdate,
    featureToBeUpdated: FeatureUpdate
) {
  featureToBeUpdated.status = featureUpdate.status;
  let scenariosToBeUpdated = new Map<string, ScenarioUpdate>(
      Object.entries(featureUpdate.scenarios)
  );
  scenariosToBeUpdated.forEach(
      (scenarioUpdate: ScenarioUpdate, scenarioKey: string) => {
        let scenarioToBeUpdated: ScenarioUpdate = featureToBeUpdated.scenarios.get(scenarioKey);
        if (scenarioToBeUpdated === undefined) {
          // add new scenario
          let scenarioUpdateNew = createNewScenarioUpdate(scenarioUpdate);
          let stepToBeUpdated = new Map<string, StepUpdate>(Object.entries(scenarioUpdate.steps));
          stepToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
            addStep(stepUpdate, scenarioUpdateNew, stepKey,
                scenarioUpdate.description, featureUpdate.description);
          });
          featureToBeUpdated.scenarios.set(scenarioKey, scenarioUpdateNew);
        } else {
          // update scenario and check steps
          scenarioToBeUpdated.status = scenarioUpdate.status;
          let stepsToBeUpdated = new Map<string, StepUpdate>(Object.entries(scenarioUpdate.steps));
          stepsToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
            let stepToBeUpdated: StepUpdate = scenarioToBeUpdated.steps.get(stepKey);
            if (stepToBeUpdated === undefined) {
              addStep(stepUpdate, scenarioToBeUpdated, stepKey,
                  scenarioUpdate.description, featureUpdate.description);
            } else {
              // update step
              stepToBeUpdated.status = stepUpdate.status;
            }
          });
        }
      }
  );
}

function addNewFeatureToMap(featureUpdate: FeatureUpdate, featureKey: string) {
  if (DEBUG) console.log("add new feature " + JSON.stringify(featureUpdate));
  let featureUpdatedNew = createNewFeatureUpdate(featureUpdate);
  let scenarioToBeUpdated = new Map<string, ScenarioUpdate>(Object.entries(featureUpdate.scenarios));
  scenarioToBeUpdated.forEach(
      (scenarioUpdate: ScenarioUpdate, scenarioKey: string) => {
        let scenarioUpdateNew = createNewScenarioUpdate(scenarioUpdate);
        let stepToBeUpdated = new Map<string, StepUpdate>(Object.entries(scenarioUpdate.steps));
        stepToBeUpdated.forEach((stepUpdate: StepUpdate, stepKey: string) => {
          addStep(stepUpdate, scenarioUpdateNew, stepKey,
              scenarioUpdate, featureUpdate
          );
        });
        featureUpdatedNew.scenarios.set(scenarioKey, scenarioUpdateNew);
      }
  );
  featureUpdateMap.value.set(featureKey, featureUpdatedNew);
}

function createNewFeatureUpdate(featureUpdate: FeatureUpdate): FeatureUpdate {
  return {
    description: featureUpdate.description,
    status: featureUpdate.status,
    scenarios: new Map<string, ScenarioUpdate>()
  };
}

function createNewScenarioUpdate(scenarioUpdate: ScenarioUpdate): ScenarioUpdate {
  return {
    description: scenarioUpdate.description,
    status: scenarioUpdate.status,
    steps: new Map<string, StepUpdate>()
  };
}

function addStep(stepUpdate: StepUpdate, scenarioToBeUpdated: ScenarioUpdate,
                 stepKey: string, scenarioUpdate: ScenarioUpdate, featureUpdate: FeatureUpdate) {
  scenarioToBeUpdated.steps.set(stepKey, {
    description: stepUpdate.description,
    status: stepUpdate.status
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

</style>
